package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogOfferingSnapshot
import com.pawsnearme.common.module.CatalogSlotSnapshot
import com.pawsnearme.common.module.CustomerPetIdentitySnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MerchantAppointmentQueryServiceTests {
    private val appointmentRepository: AppointmentRepository = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val catalogModule: CatalogModuleApi = mock()
    private val service = MerchantAppointmentQueryService(
        appointmentRepository,
        providerModule,
        catalogModule,
    )

    @Test
    fun `provider owner receives real customer pet service and slot details`() {
        val ownerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val petId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val appointmentId = UUID.randomUUID()
        val start = Instant.parse("2026-08-09T04:30:00Z")
        val end = Instant.parse("2026-08-09T05:00:00Z")
        val appointment = Appointment(
            appointmentId = appointmentId,
            customerId = customerId,
            providerId = providerId,
            offeringId = offeringId,
            slotId = slotId,
            petId = petId,
            status = AppointmentStatus.CONFIRMED,
            priceAmount = BigDecimal("500.00"),
            payAtClinic = true,
        )
        whenever(providerModule.ownerUserId(providerId)).thenReturn(ownerId)
        whenever(providerModule.customerPetIdentity(customerId, petId)).thenReturn(
            CustomerPetIdentitySnapshot(customerId, "Ananya Rao", petId, "Bruno")
        )
        whenever(catalogModule.offering(offeringId)).thenReturn(
            CatalogOfferingSnapshot(offeringId, providerId, "Vet consultation", BigDecimal("500.00"), "ACTIVE", null)
        )
        whenever(catalogModule.slot(slotId)).thenReturn(CatalogSlotSnapshot(slotId, start, end, "BOOKED"))
        whenever(appointmentRepository.findByProviderId(providerId)).thenReturn(listOf(appointment))

        val view = service.listProviderAppointments(providerId, ownerId, "MERCHANT").single()

        assertEquals("Ananya Rao", view.customerName)
        assertEquals("Bruno", view.petName)
        assertEquals("Vet consultation", view.offeringName)
        assertEquals(start, view.slotStartsAt)
        assertEquals(end, view.slotEndsAt)
    }

    @Test
    fun `missing identity stays unavailable rather than being fabricated`() {
        val ownerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val appointment = Appointment(
            appointmentId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            providerId = providerId,
            offeringId = UUID.randomUUID(),
            slotId = UUID.randomUUID(),
            petId = UUID.randomUUID(),
            status = AppointmentStatus.CONFIRMED,
            priceAmount = BigDecimal("300.00"),
        )
        whenever(providerModule.ownerUserId(providerId)).thenReturn(ownerId)
        whenever(appointmentRepository.findByProviderId(providerId)).thenReturn(listOf(appointment))

        val view = service.listProviderAppointments(providerId, ownerId, "MERCHANT").single()

        assertNull(view.customerName)
        assertNull(view.petName)
    }

    @Test
    fun `merchant cannot inspect another providers appointments`() {
        val providerId = UUID.randomUUID()
        whenever(providerModule.ownerUserId(providerId)).thenReturn(UUID.randomUUID())

        assertThrows<AppointmentAccessDeniedException> {
            service.listProviderAppointments(providerId, UUID.randomUUID(), "MERCHANT")
        }
    }
}
