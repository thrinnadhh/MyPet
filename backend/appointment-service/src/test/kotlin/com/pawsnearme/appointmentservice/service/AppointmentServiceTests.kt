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
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class AppointmentServiceTests {

    private val appointmentRepository: AppointmentRepository = mock()
    private val statusHistoryRepository: AppointmentStatusHistoryRepository = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private val redisTemplate: StringRedisTemplate = mock {
        on { opsForValue() } doReturn valueOps
    }
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()

    private val service = AppointmentService(
        appointmentRepository, statusHistoryRepository,
        redisTemplate, kafkaTemplate,
        "http://localhost:8082", 300L
    )

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val offeringId = UUID.randomUUID()
    private val slotId = UUID.randomUUID()
    private val petId = UUID.randomUUID()

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
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), any<Duration>())
        ).thenReturn(false)

        val ex = assertThrows<IllegalStateException> { service.bookAppointment(bookRequest()) }
        assertTrue(ex.message!!.contains("being booked by another customer"))
    }

    @Test
    fun `bookAppointment - slot already booked - throws IllegalStateException`() {
        whenever(
            valueOps.setIfAbsent(eq("lock:slots:$slotId"), eq("locked"), any<Duration>())
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
            valueOps.setIfAbsent(any(), any(), any<Duration>())
        ).thenReturn(false)

        val ex = assertThrows<IllegalStateException> { service.holdAppointment(bookRequest()) }
        assertTrue(ex.message!!.isNotBlank())
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
}
