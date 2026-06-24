package com.pawsnearme.discoveryservice.service

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.repository.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.*
import org.springframework.data.geo.Distance
import org.springframework.data.geo.GeoResult
import org.springframework.data.geo.GeoResults
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.core.BoundGeoOperations
import org.springframework.data.redis.core.GeoOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.domain.geo.GeoReference
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class DiscoveryServiceTests {

    private val providerRepository: ProviderRepository = mock()
    private val stringRedisTemplate: StringRedisTemplate = mock()
    private val geoOperations: GeoOperations<String, String> = mock()

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    private val discoveryService: DiscoveryService by lazy {
        whenever(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations)
        DiscoveryService(providerRepository, stringRedisTemplate)
    }

    @Test
    fun `warmup - adds active providers to Redis`() {
        val providerId = UUID.randomUUID()
        val point = geometryFactory.createPoint(Coordinate(12.34, 56.78))
        val provider = Provider(
            providerId = providerId,
            ownerUserId = UUID.randomUUID(),
            providerType = ProviderType.PET_STORE,
            fulfillmentType = FulfillmentType.DELIVERY,
            name = "Pet Store",
            addressLine = "123 Main St",
            city = "City",
            pincode = "123456",
            geoLocation = point,
            status = ProviderStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        whenever(providerRepository.findByStatus(ProviderStatus.ACTIVE)).thenReturn(listOf(provider))

        discoveryService.run()

        verify(geoOperations).add(
            eq("providers:locations"),
            eq(RedisPoint(12.34, 56.78)),
            eq(providerId.toString())
        )
    }

    @Test
    fun `kafka listener - adds provider to Redis on ProviderApproved`() {
        val providerId = UUID.randomUUID()
        val point = geometryFactory.createPoint(Coordinate(12.34, 56.78))
        val provider = Provider(
            providerId = providerId,
            ownerUserId = UUID.randomUUID(),
            providerType = ProviderType.PET_STORE,
            fulfillmentType = FulfillmentType.DELIVERY,
            name = "Pet Store",
            addressLine = "123 Main St",
            city = "City",
            pincode = "123456",
            geoLocation = point,
            status = ProviderStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val event = mapOf(
            "event_type" to "ProviderApproved",
            "provider_id" to providerId.toString()
        )
        val record = ConsumerRecord<String, Map<String, Any>>("providers.events", 0, 0L, providerId.toString(), event)

        discoveryService.handleProviderApproved(record)

        verify(geoOperations).add(
            eq("providers:locations"),
            eq(RedisPoint(12.34, 56.78)),
            eq(providerId.toString())
        )
    }

    @Test
    fun `search - falls back to PostGIS when Redis search fails`() {
        // Mock Redis search throwing exception to force fallback
        whenever(geoOperations.search(any(), any<GeoReference<String>>(), any<Distance>(), any()))
            .thenThrow(RuntimeException("Redis unavailable"))

        val providerId = UUID.randomUUID()
        val projection = mock<ProviderDistanceProjection>()
        whenever(projection.getProviderId()).thenReturn(providerId)
        whenever(projection.getProviderType()).thenReturn("PET_STORE")
        whenever(projection.getFulfillmentType()).thenReturn("DELIVERY")
        whenever(projection.getName()).thenReturn("Pet Shop")
        whenever(projection.getAddressLine()).thenReturn("Addr")
        whenever(projection.getCity()).thenReturn("City")
        whenever(projection.getPincode()).thenReturn("123")
        whenever(projection.getLongitude()).thenReturn(12.34)
        whenever(projection.getLatitude()).thenReturn(56.78)
        whenever(projection.getRatingAvg()).thenReturn(BigDecimal("4.50"))
        whenever(projection.getRatingCount()).thenReturn(10)
        whenever(projection.getDistance()).thenReturn(1500.0) // 1.5 km in meters

        whenever(providerRepository.findNearbyActiveProviders(any(), any(), any(), anyOrNull()))
            .thenReturn(listOf(projection))

        val results = discoveryService.searchNearbyProviders(12.34, 56.78, 5.0, null)

        assertNotNull(results)
        assertEquals(1, results.size)
        val res = results[0]
        assertEquals(providerId, res.providerId)
        assertEquals(1.5, res.distanceKm)
        assertEquals(ProviderType.PET_STORE, res.providerType)
    }
}
