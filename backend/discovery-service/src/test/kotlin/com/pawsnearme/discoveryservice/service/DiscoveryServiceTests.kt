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
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point as RedisPoint
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
        val provider = provider(providerId, ProviderStatus.ACTIVE)
        whenever(providerRepository.findByStatus(ProviderStatus.ACTIVE)).thenReturn(listOf(provider))

        discoveryService.run()

        verify(geoOperations).add(
            eq("providers:locations"),
            eq(RedisPoint(12.34, 56.78)),
            eq(providerId.toString())
        )
    }

    @Test
    fun `provider projection - adds provider to Redis on ProviderApproved`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider(providerId, ProviderStatus.ACTIVE)))
        val record = providerEvent(providerId, "ProviderApproved")

        discoveryService.handleProviderApproved(record)

        verify(stringRedisTemplate).delete("providers:cache:$providerId")
        verify(geoOperations).add(
            eq("providers:locations"),
            eq(RedisPoint(12.34, 56.78)),
            eq(providerId.toString())
        )
    }

    @Test
    fun `provider projection - suspended provider is evicted from cache and geo index immediately`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider(providerId, ProviderStatus.SUSPENDED)))
        val record = providerEvent(providerId, "ProviderSuspended")

        discoveryService.handleProviderApproved(record)

        verify(stringRedisTemplate).delete("providers:cache:$providerId")
        verify(geoOperations).remove("providers:locations", providerId.toString())
        verify(geoOperations, never()).add(any(), any<RedisPoint>(), any())
    }

    @Test
    fun `provider projection - reactivated provider returns to geo index`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider(providerId, ProviderStatus.ACTIVE)))

        discoveryService.handleProviderApproved(providerEvent(providerId, "ProviderReactivated"))

        verify(geoOperations).add(
            eq("providers:locations"),
            eq(RedisPoint(12.34, 56.78)),
            eq(providerId.toString())
        )
    }

    @Test
    fun `universal search never returns fabricated products or guides`() {
        whenever(providerRepository.findByStatus(ProviderStatus.ACTIVE)).thenReturn(emptyList())

        val response = discoveryService.universalSearch(
            query = "Royal Canin",
            city = "Tirupati",
            latitude = null,
            longitude = null,
            typeFilter = null
        )

        assertEquals(0, response.totalResults)
        assertTrue(response.results.isEmpty())
    }

    @Test
    fun `universal search exposes only active provider records`() {
        val active = provider(UUID.randomUUID(), ProviderStatus.ACTIVE)
        whenever(providerRepository.findByStatus(ProviderStatus.ACTIVE)).thenReturn(listOf(active))

        val response = discoveryService.universalSearch(
            query = "Pet Store",
            city = "City",
            latitude = null,
            longitude = null,
            typeFilter = "PET_SHOP"
        )

        assertEquals(1, response.totalResults)
        assertEquals(active.providerId.toString(), response.results.single().id)
    }

    @Test
    fun `search - falls back to PostGIS when Redis search fails`() {
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
        whenever(projection.getDistance()).thenReturn(1500.0)

        whenever(providerRepository.findNearbyActiveProviders(any(), any(), any(), anyOrNull()))
            .thenReturn(listOf(projection))

        val results = discoveryService.searchNearbyProviders(12.34, 56.78, 5.0, null)

        assertNotNull(results)
        assertEquals(1, results.size)
        assertEquals(providerId, results[0].providerId)
        assertEquals(1.5, results[0].distanceKm)
        assertEquals(ProviderType.PET_STORE, results[0].providerType)
    }

    private fun provider(id: UUID, status: ProviderStatus) = Provider(
        providerId = id,
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Pet Store",
        addressLine = "123 Main St",
        city = "City",
        pincode = "123456",
        geoLocation = geometryFactory.createPoint(Coordinate(12.34, 56.78)),
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun providerEvent(providerId: UUID, eventType: String) =
        ConsumerRecord<String, Map<String, Any>>(
            "providers.events",
            0,
            0L,
            providerId.toString(),
            mapOf("event_type" to eventType, "provider_id" to providerId.toString())
        )
}
