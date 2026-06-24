package com.pawsnearme.discoveryservice.service

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.repository.ProviderRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.CommandLineRunner
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DiscoveryService(
    private val providerRepository: ProviderRepository,
    private val stringRedisTemplate: StringRedisTemplate
) : CommandLineRunner {

    companion object {
        private const val GEO_KEY = "providers:locations"
    }

    // --- Startup Warmup ---
    override fun run(vararg args: String?) {
        try {
            val activeProviders = providerRepository.findByStatus(ProviderStatus.ACTIVE)
            activeProviders.forEach { provider ->
                val lng = provider.geoLocation.x
                val lat = provider.geoLocation.y
                stringRedisTemplate.opsForGeo().add(
                    GEO_KEY,
                    RedisPoint(lng, lat),
                    provider.providerId.toString()
                )
            }
            println("Redis Geo cache warmed with ${activeProviders.size} active providers.")
        } catch (e: Exception) {
            System.err.println("Failed to warm Redis Geo cache: ${e.message}")
        }
    }

    // --- Kafka Event Listener ---
    @KafkaListener(topics = ["providers.events"], groupId = "discovery-service-group")
    fun handleProviderApproved(record: ConsumerRecord<String, Map<String, Any>>) {
        val event = record.value()
        val eventType = event["event_type"] as? String
        if (eventType == "ProviderApproved") {
            val providerIdStr = event["provider_id"] as? String ?: return
            val providerId = UUID.fromString(providerIdStr)

            val providerOpt = providerRepository.findById(providerId)
            if (providerOpt.isPresent) {
                val provider = providerOpt.get()
                if (provider.status == ProviderStatus.ACTIVE) {
                    val lng = provider.geoLocation.x
                    val lat = provider.geoLocation.y
                    stringRedisTemplate.opsForGeo().add(
                        GEO_KEY,
                        RedisPoint(lng, lat),
                        provider.providerId.toString()
                    )
                    println("Kafka Sync: Added provider $providerId to Redis Geo index.")
                }
            }
        }
    }

    // --- Nearby Search ---
    fun searchNearbyProviders(
        longitude: Double,
        latitude: Double,
        radiusKm: Double,
        providerType: ProviderType?
    ): List<ProviderSearchResult> {
        return try {
            val args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending()

            val results = stringRedisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(RedisPoint(longitude, latitude)),
                Distance(radiusKm, Metrics.KILOMETERS),
                args
            )

            if (results == null || results.content.isEmpty()) {
                return queryPostgisFallback(longitude, latitude, radiusKm, providerType)
            }

            val providerIds = results.content.map { UUID.fromString(it.content.name) }
            val providers = providerRepository.findAllById(providerIds)
                .filter { it.status == ProviderStatus.ACTIVE }
                .filter { providerType == null || it.providerType == providerType }
                .associateBy { it.providerId }

            results.content.mapNotNull { geoResult ->
                val id = UUID.fromString(geoResult.content.name)
                val p = providers[id] ?: return@mapNotNull null
                ProviderSearchResult(
                    providerId = p.providerId,
                    providerType = p.providerType,
                    fulfillmentType = p.fulfillmentType,
                    name = p.name,
                    description = p.description,
                    addressLine = p.addressLine,
                    city = p.city,
                    pincode = p.pincode,
                    longitude = geoResult.content.point.x,
                    latitude = geoResult.content.point.y,
                    ratingAvg = p.ratingAvg.toDouble(),
                    ratingCount = p.ratingCount,
                    distanceKm = geoResult.distance.value
                )
            }
        } catch (e: Exception) {
            System.err.println("Redis query failed, falling back to PostGIS: ${e.message}")
            queryPostgisFallback(longitude, latitude, radiusKm, providerType)
        }
    }

    private fun queryPostgisFallback(
        longitude: Double,
        latitude: Double,
        radiusKm: Double,
        providerType: ProviderType?
    ): List<ProviderSearchResult> {
        val radiusMeters = radiusKm * 1000.0
        val projections = providerRepository.findNearbyActiveProviders(
            longitude,
            latitude,
            radiusMeters,
            providerType?.name
        )
        return projections.map {
            ProviderSearchResult(
                providerId = it.getProviderId(),
                providerType = ProviderType.valueOf(it.getProviderType()),
                fulfillmentType = FulfillmentType.valueOf(it.getFulfillmentType()),
                name = it.getName(),
                description = it.getDescription(),
                addressLine = it.getAddressLine(),
                city = it.getCity(),
                pincode = it.getPincode(),
                longitude = it.getLongitude(),
                latitude = it.getLatitude(),
                ratingAvg = it.getRatingAvg().toDouble(),
                ratingCount = it.getRatingCount(),
                distanceKm = it.getDistance() / 1000.0
            )
        }
    }
}
