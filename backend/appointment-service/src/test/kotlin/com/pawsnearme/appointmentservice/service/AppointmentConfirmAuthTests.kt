package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class AppointmentConfirmAuthTests {

    private val appointmentRepository: AppointmentRepository = mock()
    private val statusHistoryRepository: AppointmentStatusHistoryRepository = mock()
    private val invoiceRepository: AppointmentInvoiceRepository = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private val redisTemplate: StringRedisTemplate = mock {
        on { opsForValue() } doReturn valueOps
    }
    private val restOperations: RestOperations = mock()
    private val outboxService: com.pawsnearme.common.outbox.OutboxService = mock()

    private val service = AppointmentService(
        appointmentRepository, statusHistoryRepository, invoiceRepository,
        redisTemplate, mock(), restOperations, outboxService,
        "http://localhost:8082", 300L, "http://localhost:8081", "http://localhost:8090",
    )

    private val customerId = UUID.randomUUID()
    private val otherCustomerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val appointmentId = UUID.randomUUID()
    private val paymentId = UUID.randomUUID()
    private val slotId = UUID.randomUUID()

    private fun heldAppointment(payAtClinic: Boolean = false) = Appointment(
        appointmentId = appointmentId,
        customerId = customerId,
        providerId = providerId,
        offeringId = UUID.randomUUID(),
        slotId = slotId,
        petId = UUID.randomUUID(),
        priceAmount = BigDecimal("500.00"),
        status = AppointmentStatus.SLOT_HELD,
        bookedAt = Instant.now(),
        payAtClinic = payAtClinic,
    )

    private fun mockCatalogBooked() {
        whenever(restOperations.exchange(
            eq("http://localhost:8082/api/v1/catalog/slots/$slotId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(CatalogSlotSnapshot::class.java),
        )).thenReturn(org.springframework.http.ResponseEntity.ok(CatalogSlotSnapshot(slotId = slotId)))
        whenever(restOperations.exchange(
            eq("http://localhost:8082/api/v1/catalog/slots/$slotId/status?status=BOOKED"),
            eq(org.springframework.http.HttpMethod.PUT),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Void::class.java),
        )).thenReturn(org.springframework.http.ResponseEntity.ok().build())
    }

    private fun mockSuccessfulPayment() {
        whenever(restOperations.exchange(
            eq("http://localhost:8090/api/v1/payments/transactions/$paymentId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java),
        )).thenReturn(
            org.springframework.http.ResponseEntity.ok(
                mapOf(
                    "status" to "SUCCESS",
                    "referenceId" to appointmentId.toString(),
                    "userId" to customerId.toString(),
                    "transactionType" to "APPOINTMENT_PAYMENT",
                    "amount" to BigDecimal("500.00"),
                ),
            ),
        )
    }

    @Test
    fun `confirmAppointment - wrong customer without payment - throws AccessDeniedException`() {
        whenever(appointmentRepository.findById(appointmentId))
            .thenReturn(java.util.Optional.of(heldAppointment()))

        assertThrows<AppointmentAccessDeniedException> {
            service.confirmAppointment(appointmentId, paymentId, otherCustomerId, "CUSTOMER")
        }
    }

    @Test
    fun `confirmAppointment - missing payment for prepaid appointment - throws IllegalArgumentException`() {
        whenever(appointmentRepository.findById(appointmentId))
            .thenReturn(java.util.Optional.of(heldAppointment(payAtClinic = false)))

        val ex = assertThrows<IllegalArgumentException> {
            service.confirmAppointment(appointmentId, null, customerId, "CUSTOMER")
        }
        assertTrue(ex.message!!.contains("paymentId is required"))
    }

    @Test
    fun `confirmAppointment - failed payment transaction - throws IllegalStateException`() {
        whenever(appointmentRepository.findById(appointmentId))
            .thenReturn(java.util.Optional.of(heldAppointment()))
        whenever(restOperations.exchange(
            eq("http://localhost:8090/api/v1/payments/transactions/$paymentId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java),
        )).thenReturn(
            org.springframework.http.ResponseEntity.ok(
                mapOf(
                    "status" to "FAILED",
                    "referenceId" to appointmentId.toString(),
                    "userId" to customerId.toString(),
                    "transactionType" to "APPOINTMENT_PAYMENT",
                    "amount" to BigDecimal("500.00"),
                ),
            ),
        )

        val ex = assertThrows<IllegalStateException> {
            service.confirmAppointment(appointmentId, paymentId, customerId, "CUSTOMER")
        }
        assertTrue(ex.message!!.contains("not successful"))
    }

    @Test
    fun `confirmAppointment - matching customer with verified payment - succeeds`() {
        val appointment = heldAppointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(java.util.Optional.of(appointment))
        whenever(redisTemplate.hasKey("hold:slots:$slotId")).thenReturn(true)
        whenever(appointmentRepository.save(any())).thenAnswer { it.getArgument<Appointment>(0) }
        mockCatalogBooked()
        mockSuccessfulPayment()

        val saved = service.confirmAppointment(appointmentId, paymentId, customerId, "CUSTOMER")

        assertEquals(AppointmentStatus.CONFIRMED, saved.status)
        assertEquals(paymentId, saved.paymentId)
    }
}
