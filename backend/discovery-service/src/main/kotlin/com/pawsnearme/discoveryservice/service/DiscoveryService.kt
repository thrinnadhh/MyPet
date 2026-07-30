package com.pawsnearme.discoveryservice.service

import org.slf4j.LoggerFactory
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
        private val logger = LoggerFactory.getLogger(DiscoveryService::class.java)
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
            logger.info("Redis Geo cache warmed with {} active providers.", activeProviders.size)
        } catch (e: Exception) {
            logger.error("Failed to warm Redis Geo cache: {}", e.message, e)
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
                    logger.info("Kafka Sync: Added provider {} to Redis Geo index and evicted cache.", providerId)
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
            logger.info("Kafka Sync: Evicted provider cache for {} due to new review submission.", providerIdStr)
        } catch (e: Exception) {
            logger.error("Error processing review Kafka event in DiscoveryService: {}", e.message, e)
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
                        logger.error("Failed to cache provider {}: {}", id, e.message, e)
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
            logger.error("Redis query failed, falling back to PostGIS: {}", e.message, e)
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

    fun universalSearch(
        query: String,
        city: String?,
        latitude: Double?,
        longitude: Double?,
        typeFilter: String?,
        page: Int = 0,
        size: Int = 20
    ): UniversalSearchResponse {
        val q = query.trim().lowercase()
        val allItems = mutableListOf<UniversalSearchResultItem>()

        val providers = providerRepository.findByStatus(ProviderStatus.ACTIVE)
            .filter { p ->
                city.isNullOrBlank() || p.city.equals(city.trim(), ignoreCase = true)
            }
            .filter { p ->
                q.isEmpty() || p.name.lowercase().contains(q) ||
                (p.description?.lowercase()?.contains(q) == true) ||
                p.city.lowercase().contains(q)
            }

        for (p in providers) {
            val dist = if (latitude != null && longitude != null) {
                haversineKm(latitude, longitude, p.geoLocation.y, p.geoLocation.x)
            } else null

            val itemType = when (p.providerType) {
                ProviderType.PET_STORE -> "PET_SHOP"
                ProviderType.VET_HOSPITAL -> "HOSPITAL"
                ProviderType.GROOMING_CENTER -> "GROOMER"
            }

            val route = when (p.providerType) {
                ProviderType.PET_STORE -> "/shop/${p.providerId}"
                ProviderType.VET_HOSPITAL -> "/hospital/${p.providerId}"
                ProviderType.GROOMING_CENTER -> "/groomer/${p.providerId}"
            }

            val isEmergency = p.providerType == ProviderType.VET_HOSPITAL

            allItems.add(
                UniversalSearchResultItem(
                    id = p.providerId.toString(),
                    type = itemType,
                    title = p.name,
                    subtitle = "${p.addressLine}, ${p.city} • Rating ${p.ratingAvg} ★",
                    rating = "${p.ratingAvg} ★",
                    distanceKm = dist,
                    route = route,
                    isEmergency = isEmergency
                )
            )
        }

        val mockProducts = listOf(
            UniversalSearchResultItem(id = "prod-1", type = "PRODUCT", title = "Royal Canin Maxi Puppy Dry Food 3kg", subtitle = "Balanced nutrition for growing puppies", price = "₹1,850", route = "/category/food"),
            UniversalSearchResultItem(id = "prod-2", type = "PRODUCT", title = "Pedigree Chicken & Vegetables Adult", subtitle = "Complete daily meal for adult dogs", price = "₹850", route = "/category/food"),
            UniversalSearchResultItem(id = "prod-3", type = "PRODUCT", title = "Paws & Bubbles Anti-Tick Shampoo 500ml", subtitle = "Gentle herbal formula with tea tree oil", price = "₹499", route = "/category/grooming"),
            UniversalSearchResultItem(id = "prod-4", type = "PRODUCT", title = "KONG Classic Dog Toy Medium", subtitle = "Durable natural rubber chew toy", price = "₹799", route = "/category/toys")
        ).filter { q.isEmpty() || it.title.lowercase().contains(q) || (it.subtitle?.lowercase()?.contains(q) == true) }

        allItems.addAll(mockProducts)

        val mockGuides = listOf(
            UniversalSearchResultItem(id = "g-1", type = "GUIDE", title = "Puppy Nutrition Guide (0 - 2 Months)", subtitle = "Essential weaning steps & milk substitutes", route = "/guide/puppy-nutrition-0-2-mo"),
            UniversalSearchResultItem(id = "g-2", type = "GUIDE", title = "Puppy Growth Tracker (2 - 12 Months)", subtitle = "Teething relief & weight milestones", route = "/guide/puppy-growth-2-12-mo"),
            UniversalSearchResultItem(id = "g-3", type = "GUIDE", title = "Coat & Skin Health Masterclass", subtitle = "Prevent hot spots & seasonal shedding", route = "/guide/coat-skin-health")
        ).filter { q.isEmpty() || it.title.lowercase().contains(q) || (it.subtitle?.lowercase()?.contains(q) == true) }

        allItems.addAll(mockGuides)

        val filtered = if (!typeFilter.isNullOrBlank() && !typeFilter.equals("ALL", ignoreCase = true)) {
            allItems.filter { it.type.equals(typeFilter.trim(), ignoreCase = true) }
        } else {
            allItems
        }

        val fromIndex = (page * size).coerceAtMost(filtered.size)
        val toIndex = ((page + 1) * size).coerceAtMost(filtered.size)
        val pagedResults = if (fromIndex <= toIndex) filtered.subList(fromIndex, toIndex) else emptyList()

        return UniversalSearchResponse(
            query = query,
            totalResults = filtered.size,
            page = page,
            size = size,
            results = pagedResults
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

