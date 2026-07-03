package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AppointmentServiceTests {

    private val appointmentRepository: AppointmentRepository = mock()
    private val statusHistoryRepository: AppointmentStatusHistoryRepository = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private val redisTemplate: StringRedisTemplate = mock {
        on { opsForValue() } doReturn valueOps
    }
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val restOperations: RestOperations = mock()

    private val service = AppointmentService(
        appointmentRepository, statusHistoryRepository,
        redisTemplate, kafkaTemplate, restOperations,
        "http://localhost:8082", 300L
    )

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val offeringId = UUID.randomUUID()
    private val slotId = UUID.randomUUID()
    private val petId = UUID.randomUUID()
    private val appointmentId = UUID.randomUUID()

    private fun bookRequest() = BookAppointmentRequest(
        customerId = customerId,
        providerId = providerId,
        offeringId = offeringId,
        slotId = slotId,
        petId = petId,
        priceAmount = BigDecimal("500.00")
    )

    // ── bookAppointment — Redis lock guard ────────────────────────────────────

    @Test
    fun `bookAppointment - redis lock not acquired - throws IllegalStateException`() {
        whenever(
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), eq(Duration.ofSeconds(10)))
        ).thenReturn(false)

        val ex = assertThrows<IllegalStateException> { service.bookAppointment(bookRequest()) }
        assertTrue(ex.message!!.contains("being booked by another customer"))
    }

    @Test
    fun `bookAppointment - slot already booked - throws IllegalStateException`() {
        whenever(
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), eq(Duration.ofSeconds(10)))
        ).thenReturn(true)

        whenever(
            appointmentRepository.existsBySlotIdAndStatusNotIn(eq(slotId), any())
        ).thenReturn(true)

        // Cleanup delete should be called after failure
        val ex = assertThrows<IllegalStateException> { service.bookAppointment(bookRequest()) }
        assertTrue(ex.message!!.contains("already booked or held"))
    }

    // ── holdAppointment — lock guard ──────────────────────────────────────────

    @Test
    fun `holdAppointment - redis lock not acquired - throws`() {
        whenever(
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), eq(Duration.ofSeconds(10)))
        ).thenReturn(false)

        val ex = assertThrows<IllegalStateException> { service.holdAppointment(bookRequest()) }
        assertTrue(ex.message!!.isNotBlank())
    }

    @Test
    fun `holdAppointment - stores redis hold key with ttl and keeps write lock separate`() {
        whenever(
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), eq(Duration.ofSeconds(10)))
        ).thenReturn(true)
        whenever(redisTemplate.hasKey("hold:slots:$slotId")).thenReturn(false)
        whenever(appointmentRepository.existsBySlotIdAndStatusNotIn(eq(slotId), any())).thenReturn(false)
        whenever(appointmentRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<Appointment>(0).apply { appointmentId = this@AppointmentServiceTests.appointmentId }
        }

        val saved = service.holdAppointment(bookRequest())

        assertEquals(AppointmentStatus.SLOT_HELD, saved.status)
        verify(valueOps).set("hold:slots:$slotId", appointmentId.toString(), Duration.ofSeconds(300))
        verify(redisTemplate).delete("lock:slots:$slotId")
        verify(redisTemplate, never()).delete("hold:slots:$slotId")
    }

    @Test
    fun `holdAppointment - active slot guard rejects duplicate hold`() {
        whenever(
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), eq(Duration.ofSeconds(10)))
        ).thenReturn(true)
        whenever(appointmentRepository.existsBySlotIdAndStatusNotIn(eq(slotId), any())).thenReturn(true)

        val ex = assertThrows<IllegalStateException> { service.holdAppointment(bookRequest()) }

        assertTrue(ex.message!!.contains("already booked or held"))
        verify(appointmentRepository, never()).save(any())
        verify(redisTemplate).delete("lock:slots:$slotId")
    }

    @Test
    fun `confirmAppointment - books held slot clears hold key and publishes event contracts`() {
        val appointment = appointment(status = AppointmentStatus.SLOT_HELD)
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appointment))
        whenever(redisTemplate.hasKey("hold:slots:$slotId")).thenReturn(true)
        whenever(appointmentRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Appointment>(0) }

        val saved = service.confirmAppointment(appointmentId)

        assertEquals(AppointmentStatus.CONFIRMED, saved.status)
        verify(redisTemplate).delete("hold:slots:$slotId")
        verify(restOperations).put("http://localhost:8082/api/v1/catalog/slots/$slotId/status?status=BOOKED", null)
        verify(kafkaTemplate, atLeastOnce()).send(eq("appointments.events"), eq(appointmentId.toString()), check<String> {
            assertTrue(it.contains("\"event_id\""))
            assertTrue(it.contains("\"occurred_at\""))
            assertTrue(it.contains("\"actor_id\""))
            assertTrue(it.contains("\"appointment_id\""))
            assertTrue(it.contains("\"slot_id\""))
        })
    }

    @Test
    fun `confirmAppointment - expired hold releases slot and clears redis hold key`() {
        val appointment = appointment(
            status = AppointmentStatus.SLOT_HELD,
            bookedAt = Instant.now().minusSeconds(301)
        )
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appointment))
        whenever(appointmentRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Appointment>(0) }

        val ex = assertThrows<IllegalStateException> { service.confirmAppointment(appointmentId) }

        assertTrue(ex.message!!.contains("expired"))
        assertEquals(AppointmentStatus.EXPIRED, appointment.status)
        verify(redisTemplate).delete("hold:slots:$slotId")
        verify(restOperations).put("http://localhost:8082/api/v1/catalog/slots/$slotId/status?status=AVAILABLE", null)
    }

    // ── updateAppointmentStatus ───────────────────────────────────────────────

    @Test
    fun `updateAppointmentStatus - appointment not found - throws NoSuchElementException`() {
        val apptId = UUID.randomUUID()
        whenever(appointmentRepository.findById(apptId)).thenReturn(java.util.Optional.empty())

        val ex = assertThrows<NoSuchElementException> {
            service.updateAppointmentStatus(apptId, AppointmentStatus.COMPLETED, customerId, null, null)
        }
        assertTrue(ex.message!!.contains("not found"))
    }

    @Test
    fun `updateAppointmentStatus - publishes snake case status event`() {
        val appointment = appointment(status = AppointmentStatus.CONFIRMED)
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appointment))
        whenever(appointmentRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Appointment>(0) }

        service.updateAppointmentStatus(appointmentId, AppointmentStatus.COMPLETED, customerId, "visit done", "https://example.com/rx.pdf")

        verify(kafkaTemplate).send(eq("appointments.events"), eq(appointmentId.toString()), check<String> {
            assertTrue(it.contains("\"event_id\""))
            assertTrue(it.contains("\"event_type\":\"AppointmentStatusChanged\""))
            assertTrue(it.contains("\"from_status\":\"CONFIRMED\""))
            assertTrue(it.contains("\"to_status\":\"COMPLETED\""))
        })
    }

    private fun appointment(
        status: AppointmentStatus,
        bookedAt: Instant = Instant.now()
    ) = Appointment(
        appointmentId = appointmentId,
        customerId = customerId,
        providerId = providerId,
        offeringId = offeringId,
        slotId = slotId,
        petId = petId,
        status = status,
        priceAmount = BigDecimal("500.00"),
        bookedAt = bookedAt
    )
}
