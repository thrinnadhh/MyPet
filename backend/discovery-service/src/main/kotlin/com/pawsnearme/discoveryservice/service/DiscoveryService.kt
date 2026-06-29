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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class ProviderCacheDto(
    val providerId: String = "",
    val ownerUserId: String = "",
    val providerType: String = "",
    val fulfillmentType: String = "",
    val name: String = "",
    val description: String? = null,
    val licenseNumber: String? = null,
    val licenseDocUrl: String? = null,
    val addressLine: String = "",
    val city: String = "",
    val pincode: String = "",
    val status: String = "",
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0
)

@Service
@Transactional(readOnly = true)
class DiscoveryService(
    private val providerRepository: ProviderRepository,
    private val stringRedisTemplate: StringRedisTemplate
) : CommandLineRunner {

    companion object {
        private const val GEO_KEY = "providers:locations"
        private const val CACHE_PREFIX = "providers:cache:"
    }

    private val objectMapper = ObjectMapper().registerKotlinModule()

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
                
                // Evict cache to ensure fresh data on first queries
                stringRedisTemplate.delete("$CACHE_PREFIX${provider.providerId}")
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

            // Evict cache
            stringRedisTemplate.delete("$CACHE_PREFIX$providerIdStr")

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
                    println("Kafka Sync: Added provider $providerId to Redis Geo index and evicted cache.")
                }
            }
        }
    }

    @KafkaListener(topics = ["reviews.events"], groupId = "discovery-service-reviews-group")
    fun handleReviewSubmitted(record: ConsumerRecord<String, Map<String, Any>>) {
        try {
            val event = record.value()
            val providerIdStr = event["providerId"] as? String ?: event["provider_id"] as? String ?: return
            stringRedisTemplate.delete("$CACHE_PREFIX$providerIdStr")
            println("Kafka Sync: Evicted provider cache for $providerIdStr due to new review submission.")
        } catch (e: Exception) {
            System.err.println("Error processing review Kafka event in DiscoveryService: ${e.message}")
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
            
            // 1. Try fetching from Redis Cache
            val cacheResults = providerIds.map { id ->
                val json = stringRedisTemplate.opsForValue().get("$CACHE_PREFIX$id")
                if (json != null) {
                    try {
                        objectMapper.readValue(json, ProviderCacheDto::class.java)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }

            // 2. Determine misses and query DB
            val missIds = providerIds.zip(cacheResults)
                .filter { it.second == null }
                .map { it.first }

            val dbFetched = if (missIds.isNotEmpty()) {
                providerRepository.findAllById(missIds)
                    .associateBy { it.providerId }
            } else {
                emptyMap()
            }

            // 3. Re-assemble final providers list & save misses to cache
            val finalProviders = providerIds.zip(cacheResults).mapNotNull { (id, cached) ->
                if (cached != null) {
                    cached
                } else {
                    val p = dbFetched[id] ?: return@mapNotNull null
                    val dto = ProviderCacheDto(
                        providerId = p.providerId.toString(),
                        ownerUserId = p.ownerUserId.toString(),
                        providerType = p.providerType.name,
                        fulfillmentType = p.fulfillmentType.name,
                        name = p.name,
                        description = p.description,
                        licenseNumber = null,
                        licenseDocUrl = null,
                        addressLine = p.addressLine,
                        city = p.city,
                        pincode = p.pincode,
                        status = p.status.name,
                        ratingAvg = p.ratingAvg.toDouble(),
                        ratingCount = p.ratingCount
                    )
                    
                    // Cache metadata in Redis (5-minute TTL)
                    try {
                        val json = objectMapper.writeValueAsString(dto)
                        stringRedisTemplate.opsForValue().set(
                            "$CACHE_PREFIX$id",
                            json,
                            java.time.Duration.ofSeconds(300)
                        )
                    } catch (e: Exception) {
                        System.err.println("Failed to cache provider $id: ${e.message}")
                    }
                    dto
                }
            }.filter { it.status == "ACTIVE" }
             .filter { providerType == null || it.providerType == providerType.name }
             .associateBy { UUID.fromString(it.providerId) }

            results.content.mapNotNull { geoResult ->
                val id = UUID.fromString(geoResult.content.name)
                val p = finalProviders[id] ?: return@mapNotNull null
                ProviderSearchResult(
                    providerId = UUID.fromString(p.providerId),
                    providerType = ProviderType.valueOf(p.providerType),
                    fulfillmentType = FulfillmentType.valueOf(p.fulfillmentType),
                    name = p.name,
                    description = p.description,
                    addressLine = p.addressLine,
                    city = p.city,
                    pincode = p.pincode,
                    longitude = geoResult.content.point.x,
                    latitude = geoResult.content.point.y,
                    ratingAvg = p.ratingAvg,
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
