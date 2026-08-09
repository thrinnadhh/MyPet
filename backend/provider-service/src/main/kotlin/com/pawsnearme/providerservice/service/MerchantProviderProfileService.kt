package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.controller.ProviderAccessDeniedException
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Merchant-owned edits to customer-visible business profile data.
 *
 * Trust-sensitive fields (owner, provider/fulfilment type, approval status,
 * commission and veterinary licence identity) are intentionally excluded.
 */
@Service
class MerchantProviderProfileService(
    private val providerRepository: ProviderRepository,
    private val outboxService: OutboxService
) {
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Transactional
    fun updateProfile(
        providerId: UUID,
        actorUserId: UUID,
        name: String,
        description: String?,
        addressLine: String,
        city: String,
        pincode: String,
        longitude: Double,
        latitude: Double
    ): Provider {
        val provider = providerRepository.findById(providerId).orElseThrow {
            NoSuchElementException("Provider with ID $providerId not found")
        }
        if (provider.ownerUserId != actorUserId) {
            throw ProviderAccessDeniedException("Access denied to another merchant's provider")
        }

        provider.name = name.trim()
        provider.description = description?.trim()?.takeIf { it.isNotEmpty() }
        provider.addressLine = addressLine.trim()
        provider.city = city.trim()
        provider.pincode = pincode.trim()
        provider.geoLocation = geometryFactory.createPoint(Coordinate(longitude, latitude))

        val saved = providerRepository.save(provider)
        val eventId = UUID.randomUUID()
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "PROVIDER",
            aggregateId = requireNotNull(saved.providerId),
            eventType = "ProviderProfileUpdated",
            eventPayload = mapOf(
                "event_id" to eventId.toString(),
                "event_type" to "ProviderProfileUpdated",
                "occurred_at" to Instant.now().toString(),
                "actor_id" to actorUserId.toString(),
                "provider_id" to saved.providerId.toString(),
                "name" to saved.name,
                "city" to saved.city,
                "pincode" to saved.pincode,
                "longitude" to saved.geoLocation.x,
                "latitude" to saved.geoLocation.y
            )
        )
        return saved
    }
}
