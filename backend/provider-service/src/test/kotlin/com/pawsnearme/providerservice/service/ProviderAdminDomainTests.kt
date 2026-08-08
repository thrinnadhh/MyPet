package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.repository.ProfileRepository
import com.pawsnearme.providerservice.repository.ProviderDocumentRepository
import com.pawsnearme.providerservice.repository.ProviderRepository
import com.pawsnearme.providerservice.repository.UserRoleJoinRepository
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
import org.springframework.kafka.core.KafkaTemplate
import java.util.Optional
import java.util.UUID

class ProviderAdminDomainTests {
    private val providerRepository: ProviderRepository = mock()
    private val providerDocumentRepository: ProviderDocumentRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val userRoleJoinRepository: UserRoleJoinRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val outboxService: OutboxService = mock()
    private val service = ProviderService(
        providerRepository,
        providerDocumentRepository,
        profileRepository,
        userRoleJoinRepository,
        kafkaTemplate,
        outboxService
    )
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `admin rejection persists state and writes auditable outbox event under row lock`() {
        val providerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val provider = provider(providerId, ProviderStatus.PENDING_APPROVAL)
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))
        whenever(providerRepository.save(any<Provider>())).thenAnswer { it.getArgument(0) }
        val payload = argumentCaptor<Any>()

        val result = service.rejectProvider(providerId, actorId, "Licence document could not be verified")

        assertEquals(ProviderStatus.REJECTED, result.status)
        verify(providerRepository).findByIdForUpdate(providerId)
        verify(providerRepository).save(provider)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(providerId),
            eventType = eq("ProviderRejected"),
            eventPayload = payload.capture()
        )
        @Suppress("UNCHECKED_CAST")
        val event = payload.firstValue as Map<String, Any?>
        assertEquals(actorId.toString(), event["actor_id"])
        assertEquals(providerId.toString(), event["provider_id"])
        assertEquals("PENDING_APPROVAL", event["previous_status"])
        assertEquals("REJECTED", event["new_status"])
        assertEquals("Licence document could not be verified", event["reason"])
    }

    @Test
    fun `admin rejection refuses invalid state without writing event`() {
        val providerId = UUID.randomUUID()
        val provider = provider(providerId, ProviderStatus.ACTIVE)
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))

        assertThrows<IllegalStateException> {
            service.rejectProvider(providerId, UUID.randomUUID(), "No longer eligible")
        }

        verify(providerRepository).findByIdForUpdate(providerId)
        verify(providerRepository, never()).save(any<Provider>())
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any())
    }

    @Test
    fun `admin rejection requires an operational reason before lock lookup`() {
        assertThrows<IllegalArgumentException> {
            service.rejectProvider(UUID.randomUUID(), UUID.randomUUID(), "  ")
        }

        verify(providerRepository, never()).findByIdForUpdate(any())
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any())
    }

    @Test
    fun `approval and rejection share the same serialized pending state boundary`() {
        val providerId = UUID.randomUUID()
        val provider = provider(providerId, ProviderStatus.PENDING_APPROVAL)
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))
        whenever(providerRepository.save(any<Provider>())).thenAnswer { it.getArgument(0) }

        val approved = service.approveProvider(providerId)
        assertEquals(ProviderStatus.ACTIVE, approved.status)

        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(approved))
        assertThrows<IllegalStateException> {
            service.rejectProvider(providerId, UUID.randomUUID(), "Concurrent stale rejection")
        }

        verify(providerRepository, org.mockito.kotlin.times(2)).findByIdForUpdate(providerId)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(providerId),
            eventType = eq("ProviderApproved"),
            eventPayload = any()
        )
    }

    private fun provider(providerId: UUID, status: ProviderStatus) = Provider(
        providerId = providerId,
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
