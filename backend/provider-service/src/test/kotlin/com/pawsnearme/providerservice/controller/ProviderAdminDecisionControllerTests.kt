package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.service.ProviderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ProviderAdminDecisionControllerTests {
    private val providerService: ProviderService = mock()
    private val controller = ProviderAdminDecisionController(providerService)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `non admin cannot reject provider`() {
        assertThrows<ProviderAccessDeniedException> {
            controller.rejectProvider(
                providerId = UUID.randomUUID(),
                userId = UUID.randomUUID().toString(),
                role = "MERCHANT",
                request = RejectProviderRequest("Invalid licence")
            )
        }
        verify(providerService, never()).rejectProvider(any(), any(), any())
    }

    @Test
    fun `admin rejection requires authenticated actor identity`() {
        assertThrows<ProviderAccessDeniedException> {
            controller.rejectProvider(
                providerId = UUID.randomUUID(),
                userId = null,
                role = "ADMIN",
                request = RejectProviderRequest("Invalid licence")
            )
        }
        verify(providerService, never()).rejectProvider(any(), any(), any())
    }

    @Test
    fun `admin rejection delegates to domain service with actor and reason`() {
        val providerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val provider = Provider(
            providerId = providerId,
            ownerUserId = UUID.randomUUID(),
            providerType = ProviderType.PET_STORE,
            fulfillmentType = FulfillmentType.DELIVERY,
            name = "Happy Tails",
            addressLine = "12 Main Road",
            city = "Tirupati",
            pincode = "517501",
            geoLocation = geometryFactory.createPoint(Coordinate(79.4192, 13.6288)),
            status = ProviderStatus.REJECTED
        )
        whenever(providerService.rejectProvider(providerId, actorId, "Invalid licence")).thenReturn(provider)

        val response = controller.rejectProvider(
            providerId = providerId,
            userId = actorId.toString(),
            role = "ADMIN",
            request = RejectProviderRequest("Invalid licence")
        )

        assertEquals(ProviderStatus.REJECTED, response.body?.status)
        verify(providerService).rejectProvider(providerId, actorId, "Invalid licence")
    }
}
