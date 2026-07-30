package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.repository.ProviderRepository
import com.pawsnearme.providerservice.service.ProviderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID

class ProviderControllerTests {
    private val providerService: ProviderService = mock()
    private val providerRepository: ProviderRepository = mock()
    private val controller = ProviderController(providerService, providerRepository)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `updateCommission rejects non-admin caller`() {
        val providerId = UUID.randomUUID()

        assertThrows<ProviderAccessDeniedException> {
            controller.updateCommission(
                providerId,
                "MERCHANT",
                UUID.randomUUID().toString(),
                UpdateProviderCommissionRequest(BigDecimal("16.00"), "Not allowed")
            )
        }
        verify(providerService, never()).updateCommission(any(), any(), any(), any())
    }

    @Test
    fun `updateCommission allows admin caller`() {
        val providerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        whenever(providerService.updateCommission(providerId, BigDecimal("16.00"), actorId, "Rate review"))
            .thenReturn(sampleProvider(providerId, BigDecimal("16.00")))

        val response = controller.updateCommission(
            providerId,
            "ADMIN",
            actorId.toString(),
            UpdateProviderCommissionRequest(BigDecimal("16.00"), "Rate review")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(providerService).updateCommission(providerId, BigDecimal("16.00"), actorId, "Rate review")
    }

    @Test
    fun `createProvider rejects merchant spoofing another owner`() {
        val actorId = UUID.randomUUID()
        val request = CreateProviderRequest(
            ownerUserId = UUID.randomUUID(),
            providerType = ProviderType.PET_STORE,
            fulfillmentType = FulfillmentType.DELIVERY,
            name = "Spoofed Store",
            description = null,
            licenseNumber = null,
            licenseDocUrl = null,
            addressLine = "12 Main Road",
            city = "Bengaluru",
            pincode = "560001",
            longitude = 77.5946,
            latitude = 12.9716
        )

        assertThrows<ProviderAccessDeniedException> {
            controller.createProvider(request, actorId.toString(), "MERCHANT")
        }

        verify(providerService, never()).createProvider(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `getPendingProviders rejects non-admin caller`() {
        assertThrows<ProviderAccessDeniedException> {
            controller.getPendingProviders("MERCHANT")
        }

        verify(providerRepository, never()).findAll()
    }

    @Test
    fun `getProvidersByOwner rejects access to another owner`() {
        assertThrows<ProviderAccessDeniedException> {
            controller.getProvidersByOwner(
                ownerUserId = UUID.randomUUID(),
                userId = UUID.randomUUID().toString(),
                userRole = "MERCHANT"
            )
        }

        verify(providerRepository, never()).findByOwnerUserId(any())
    }

    private fun sampleProvider(providerId: UUID, commissionPct: BigDecimal) = Provider(
        providerId = providerId,
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Happy Tails",
        addressLine = "12 Main Road",
        city = "Bengaluru",
        pincode = "560001",
        geoLocation = geometryFactory.createPoint(Coordinate(77.5946, 12.9716)),
        status = ProviderStatus.ACTIVE,
        commissionPct = commissionPct
    )
}
