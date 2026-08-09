package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.controller.ProviderAccessDeniedException
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
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class MerchantProviderProfileServiceTests {
    private val providerRepository: ProviderRepository = mock()
    private val outboxService: OutboxService = mock()
    private val service = MerchantProviderProfileService(providerRepository, outboxService)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    private fun provider(ownerId: UUID): Provider = Provider(
        providerId = UUID.randomUUID(),
        ownerUserId = ownerId,
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Old Store",
        description = "Old description",
        licenseNumber = null,
        licenseDocUrl = "https://example.invalid/business-proof.pdf",
        addressLine = "Old address",
        city = "Tirupati",
        pincode = "517501",
        geoLocation = geometryFactory.createPoint(Coordinate(79.4192, 13.6288)),
        status = ProviderStatus.ACTIVE
    )

    @Test
    fun `merchant can update customer-visible fields without mutating trust fields`() {
        val ownerId = UUID.randomUUID()
        val existing = provider(ownerId)
        whenever(providerRepository.findById(existing.providerId!!)).thenReturn(Optional.of(existing))
        whenever(providerRepository.save(existing)).thenReturn(existing)

        val result = service.updateProfile(
            providerId = existing.providerId!!,
            actorUserId = ownerId,
            name = "  MyPet Tirupati Store  ",
            description = "  Updated profile  ",
            addressLine = "  10 Bazaar Street  ",
            city = "  Tirupati  ",
            pincode = "517501",
            longitude = 79.421,
            latitude = 13.63
        )

        assertEquals("MyPet Tirupati Store", result.name)
        assertEquals("Updated profile", result.description)
        assertEquals("10 Bazaar Street", result.addressLine)
        assertEquals(79.421, result.geoLocation.x, 0.000001)
        assertEquals(13.63, result.geoLocation.y, 0.000001)
        assertEquals(ProviderStatus.ACTIVE, result.status)
        assertEquals(ProviderType.PET_STORE, result.providerType)
        assertEquals(FulfillmentType.DELIVERY, result.fulfillmentType)
        assertEquals(null, result.licenseNumber)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(existing.providerId!!),
            eventType = eq("ProviderProfileUpdated"),
            eventPayload = any()
        )
    }

    @Test
    fun `merchant cannot update another merchant provider`() {
        val ownerId = UUID.randomUUID()
        val attackerId = UUID.randomUUID()
        val existing = provider(ownerId)
        whenever(providerRepository.findById(existing.providerId!!)).thenReturn(Optional.of(existing))

        assertThrows<ProviderAccessDeniedException> {
            service.updateProfile(
                providerId = existing.providerId!!,
                actorUserId = attackerId,
                name = "Hijacked",
                description = null,
                addressLine = "Other address",
                city = "Other city",
                pincode = "517501",
                longitude = 79.0,
                latitude = 13.0
            )
        }

        verify(providerRepository, never()).save(any())
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any())
    }
}
