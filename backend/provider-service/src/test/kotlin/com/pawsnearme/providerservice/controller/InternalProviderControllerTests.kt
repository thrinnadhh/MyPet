package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InternalProviderControllerTests {
    private val repository: ProviderRepository = mock()
    private val controller = InternalProviderController(repository, "test-internal-secret")

    @Test
    fun `owner lookup requires the internal service secret`() {
        assertThrows<ProviderAccessDeniedException> {
            controller.getProviderOwner(UUID.randomUUID(), "wrong-secret")
        }
    }

    @Test
    fun `owner lookup returns only internal ownership data`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val provider = Provider(
            providerId = providerId,
            ownerUserId = ownerId,
            providerType = ProviderType.PET_STORE,
            fulfillmentType = FulfillmentType.DELIVERY,
            name = "Internal Store",
            addressLine = "Private address",
            city = "Tirupati",
            pincode = "517501",
            geoLocation = GeometryFactory().createPoint(Coordinate(79.4192, 13.6288)),
            status = ProviderStatus.ACTIVE
        )
        whenever(repository.findById(providerId)).thenReturn(java.util.Optional.of(provider))

        val response = controller.getProviderOwner(providerId, "test-internal-secret")

        assertEquals(providerId, response.body?.providerId)
        assertEquals(ownerId, response.body?.ownerUserId)
    }
}
