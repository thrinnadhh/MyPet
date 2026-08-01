package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.repository.AppointmentInvoiceRepository
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.repository.AppointmentStatusHistoryRepository
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogSlotSnapshot
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.outbox.OutboxService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class AppointmentPaymentPreferenceTests {
    private val appointmentRepository: AppointmentRepository = mock()
    private val statusHistoryRepository: AppointmentStatusHistoryRepository = mock()
    private val invoiceRepository: AppointmentInvoiceRepository = mock()
    private val valueOperations: ValueOperations<String, String> = mock()
    private val redisTemplate: StringRedisTemplate = mock {
        on { opsForValue() } doReturn valueOperations
    }
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val outboxService: OutboxService = mock()
    private val catalogModule: CatalogModuleApi = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val paymentModule: PaymentModuleApi = mock()

    private val service = AppointmentService(
        appointmentRepository = appointmentRepository,
        appointmentStatusHistoryRepository = statusHistoryRepository,
        appointmentInvoiceRepository = invoiceRepository,
        redisTemplate = redisTemplate,
        kafkaTemplate = kafkaTemplate,
        outboxService = outboxService,
        catalogModule = catalogModule,
        providerModule = providerModule,
        paymentModule = paymentModule,
        holdDurationSeconds = 300L
    )

    private val request = BookAppointmentRequest(
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        offeringId = UUID.randomUUID(),
        slotId = UUID.randomUUID(),
        petId = UUID.randomUUID(),
        priceAmount = BigDecimal("500.00"),
        payAtClinic = true
    )

    @Test
    fun `held appointment persists pay-at-clinic preference`() {
        prepareSuccessfulCreation("HELD")

        val saved = service.holdAppointment(request)

        assertEquals(AppointmentStatus.SLOT_HELD, saved.status)
        assertTrue(saved.payAtClinic)
        verify(appointmentRepository).save(
            org.mockito.kotlin.check<Appointment> { assertTrue(it.payAtClinic) }
        )
    }

    @Test
    fun `direct appointment persists pay-at-clinic preference`() {
        prepareSuccessfulCreation("BOOKED")

        val saved = service.bookAppointment(request)

        assertEquals(AppointmentStatus.CONFIRMED, saved.status)
        assertTrue(saved.payAtClinic)
        verify(appointmentRepository).save(
            org.mockito.kotlin.check<Appointment> { assertTrue(it.payAtClinic) }
        )
    }

    private fun prepareSuccessfulCreation(slotStatus: String) {
        whenever(
            valueOperations.setIfAbsent(
                "lock:slots:${request.slotId}",
                "locked",
                Duration.ofSeconds(10)
            )
        ).thenReturn(true)
        whenever(appointmentRepository.existsBySlotIdAndStatusNotIn(any(), any())).thenReturn(false)
        whenever(catalogModule.updateSlotStatus(request.slotId, slotStatus)).thenReturn(
            CatalogSlotSnapshot(
                slotId = request.slotId,
                slotStart = null,
                slotEnd = null,
                status = slotStatus
            )
        )
        whenever(appointmentRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<Appointment>(0).apply { appointmentId = UUID.randomUUID() }
        }
    }
}
