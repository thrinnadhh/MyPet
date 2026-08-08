package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.repository.AppointmentStatusHistoryRepository
import com.pawsnearme.appointmentservice.service.AppointmentLifecyclePolicy
import com.pawsnearme.appointmentservice.service.AppointmentService
import com.pawsnearme.appointmentservice.service.BookAppointmentRequest
import com.pawsnearme.appointmentservice.service.MerchantAppointmentQueryService
import com.pawsnearme.common.module.ProviderModuleApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID

class AppointmentControllerAdminLifecycleTests {
    private val appointmentService: AppointmentService = mock()
    private val historyRepository: AppointmentStatusHistoryRepository = mock()
    private val lifecyclePolicy: AppointmentLifecyclePolicy = mock()
    private val merchantQueryService: MerchantAppointmentQueryService = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val controller = AppointmentController(
        appointmentService,
        historyRepository,
        lifecyclePolicy,
        merchantQueryService,
        providerModule
    )

    @Test
    fun `suspended provider cannot receive new appointment`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val request = request(customerId, providerId)
        whenever(providerModule.providerOperational(providerId)).thenReturn(false)

        val response = controller.bookAppointment(request, customerId.toString())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, String>
        assertEquals("PROVIDER_NOT_OPERATIONAL", body["code"])
        verify(appointmentService, never()).bookAppointment(any())
    }

    @Test
    fun `suspended provider cannot receive appointment hold`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val request = request(customerId, providerId)
        whenever(providerModule.providerOperational(providerId)).thenReturn(false)

        val response = controller.holdAppointment(request, customerId.toString())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        verify(appointmentService, never()).holdAppointment(any())
    }

    private fun request(customerId: UUID, providerId: UUID) = BookAppointmentRequest(
        customerId = customerId,
        providerId = providerId,
        offeringId = UUID.randomUUID(),
        slotId = UUID.randomUUID(),
        petId = UUID.randomUUID(),
        priceAmount = BigDecimal("499.00"),
        payAtClinic = false
    )
}
