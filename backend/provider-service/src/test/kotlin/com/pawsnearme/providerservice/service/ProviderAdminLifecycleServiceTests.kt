package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class ProviderAdminLifecycleServiceTests {
    private val providerRepository: ProviderRepository = mock()
    private val outboxService: OutboxService = mock()
    private val service = ProviderAdminLifecycleService(providerRepository, outboxService)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `active provider suspension is persisted with actor reason and event under row lock`() {
        val providerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val provider = provider(providerId, ProviderStatus.ACTIVE)
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))
        whenever(providerRepository.save(any<Provider>())).thenAnswer { it.getArgument(0) }
        val payload = argumentCaptor<Any>()

        val result = service.suspendProvider(providerId, actorId, "Repeated fulfilment safety incidents")

        assertEquals(ProviderStatus.SUSPENDED, result.status)
        verify(providerRepository).findByIdForUpdate(providerId)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(providerId),
            eventType = eq("ProviderSuspended"),
            eventPayload = payload.capture()
        )
        @Suppress("UNCHECKED_CAST")
        val event = payload.firstValue as Map<String, Any?>
        assertEquals(actorId.toString(), event["actor_id"])
        assertEquals("ACTIVE", event["previous_status"])
        assertEquals("SUSPENDED", event["new_status"])
        assertEquals("Repeated fulfilment safety incidents", event["reason"])
    }

    @Test
    fun `suspended provider can be reactivated without creating another provider`() {
        val providerId = UUID.randomUUID()
        val provider = provider(providerId, ProviderStatus.SUSPENDED)
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))
        whenever(providerRepository.save(any<Provider>())).thenAnswer { it.getArgument(0) }

        val result = service.reactivateProvider(providerId, UUID.randomUUID(), "Compliance review passed")

        assertEquals(ProviderStatus.ACTIVE, result.status)
        verify(providerRepository).findByIdForUpdate(providerId)
        verify(providerRepository).save(provider)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(providerId),
            eventType = eq("ProviderReactivated"),
            eventPayload = any()
        )
    }

    @Test
    fun `suspending non active provider is rejected under lock without event`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider(providerId, ProviderStatus.PENDING_APPROVAL)))

        assertThrows<IllegalStateException> {
            service.suspendProvider(providerId, UUID.randomUUID(), "Not operational")
        }

        verify(providerRepository).findByIdForUpdate(providerId)
        verify(providerRepository, never()).save(any<Provider>())
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any())
    }

    private fun provider(id: UUID, status: ProviderStatus) = Provider(
        providerId = id,
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Happy Tails",
        addressLine = "12 Main Road",
        city = "Tirupati",
        pincode = "517501",
        geoLocation = geometryFactory.createPoint(Coordinate(79.4192, 13.6288)),
        status = status
    )
}
