package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class AppointmentAuthTests {

    private val appointmentRepository: AppointmentRepository = mock()
    private val statusHistoryRepository: AppointmentStatusHistoryRepository = mock()
    private val invoiceRepository: AppointmentInvoiceRepository = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val restOperations: RestOperations = mock()
    private val outboxService: com.pawsnearme.common.outbox.OutboxService = mock()

    private val service = AppointmentService(
        appointmentRepository, statusHistoryRepository, invoiceRepository,
        redisTemplate, mock(), restOperations, outboxService,
        "http://localhost:8082", 300L, "http://localhost:8081"
    )

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val appointmentId = UUID.randomUUID()

    private fun appointment() = Appointment(
        appointmentId = appointmentId,
        customerId = customerId,
        providerId = providerId,
        offeringId = UUID.randomUUID(),
        slotId = UUID.randomUUID(),
        petId = UUID.randomUUID(),
        priceAmount = BigDecimal("500.00"),
        status = AppointmentStatus.CONFIRMED,
        bookedAt = Instant.now()
    )

    @Test
    fun `getAppointment - ADMIN caller - succeeds`() {
        val appt = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appt))

        val result = service.getAppointment(appointmentId, UUID.randomUUID(), "ADMIN")
        assertEquals(appt.appointmentId, result.appointmentId)
    }

    @Test
    fun `getAppointment - matching customer caller - succeeds`() {
        val appt = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appt))

        val result = service.getAppointment(appointmentId, customerId, "CUSTOMER")
        assertEquals(appt.appointmentId, result.appointmentId)
    }

    @Test
    fun `getAppointment - mismatched customer caller - throws AccessDeniedException`() {
        val appt = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appt))

        assertThrows<AppointmentAccessDeniedException> {
            service.getAppointment(appointmentId, UUID.randomUUID(), "CUSTOMER")
        }
    }

    @Test
    fun `getAppointment - verified provider staff caller - succeeds`() {
        val appt = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appt))
        
        // Mock provider owner lookup
        val merchantId = UUID.randomUUID()
        whenever(restOperations.exchange(
            eq("http://localhost:8081/api/v1/providers/$providerId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(mapOf("ownerUserId" to merchantId.toString())))

        val result = service.getAppointment(appointmentId, merchantId, "MERCHANT")
        assertEquals(appt.appointmentId, result.appointmentId)
    }

    @Test
    fun `getAppointment - mismatched provider staff caller - throws AccessDeniedException`() {
        val appt = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appt))
        
        // Mock provider owner lookup
        whenever(restOperations.exchange(
            eq("http://localhost:8081/api/v1/providers/$providerId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(mapOf("ownerUserId" to UUID.randomUUID().toString())))

        assertThrows<AppointmentAccessDeniedException> {
            service.getAppointment(appointmentId, UUID.randomUUID(), "MERCHANT")
        }
    }

    @Test
    fun `getAppointmentsByCustomer - matching customer - succeeds`() {
        val list = listOf(appointment())
        whenever(appointmentRepository.findByCustomerId(customerId)).thenReturn(list)

        val result = service.getAppointmentsByCustomer(customerId, customerId, "CUSTOMER")
        assertEquals(1, result.size)
    }

    @Test
    fun `getAppointmentsByCustomer - unprivileged customer - throws AccessDeniedException`() {
        assertThrows<AppointmentAccessDeniedException> {
            service.getAppointmentsByCustomer(customerId, UUID.randomUUID(), "CUSTOMER")
        }
    }

    @Test
    fun `getAppointmentsByProvider - verified merchant owner - succeeds`() {
        val list = listOf(appointment())
        whenever(appointmentRepository.findByProviderId(providerId)).thenReturn(list)

        val merchantId = UUID.randomUUID()
        whenever(restOperations.exchange(
            eq("http://localhost:8081/api/v1/providers/$providerId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(mapOf("ownerUserId" to merchantId.toString())))

        val result = service.getAppointmentsByProvider(providerId, merchantId, "MERCHANT")
        assertEquals(1, result.size)
    }

    @Test
    fun `updateAppointmentStatus - mismatched merchant owner - throws AccessDeniedException`() {
        val appt = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appt))

        whenever(restOperations.exchange(
            eq("http://localhost:8081/api/v1/providers/$providerId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(mapOf("ownerUserId" to UUID.randomUUID().toString())))

        assertThrows<AppointmentAccessDeniedException> {
            service.updateAppointmentStatus(
                appointmentId, AppointmentStatus.COMPLETED, UUID.randomUUID(),
                note = null, prescriptionDocUrl = null, callerRole = "MERCHANT"
            )
        }
    }
}
