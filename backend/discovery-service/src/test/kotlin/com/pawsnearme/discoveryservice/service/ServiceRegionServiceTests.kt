package com.pawsnearme.discoveryservice.service

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.repository.ServiceRegionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.Optional
import java.util.UUID

class ServiceRegionServiceTests {

    private lateinit var serviceRegionRepository: ServiceRegionRepository
    private lateinit var serviceRegionService: ServiceRegionService

    @BeforeEach
    fun setUp() {
        serviceRegionRepository = mock()
        serviceRegionService = ServiceRegionService(serviceRegionRepository)
    }

    @Test
    fun `getActiveRegions - returns enabled regions sorted by sortOrder`() {
        val tirupati = ServiceRegion(
            cityIdentity = "tirupati",
            displayName = "Tirupati",
            state = "Andhra Pradesh",
            centerLatitude = 13.6288,
            centerLongitude = 79.4192,
            status = RegionStatus.ENABLED,
            sortOrder = 1
        )
        whenever(serviceRegionRepository.findAllByStatusOrderBySortOrderAsc(RegionStatus.ENABLED)).thenReturn(listOf(tirupati))

        val result = serviceRegionService.getActiveRegions()

        assertEquals(1, result.size)
        assertEquals("tirupati", result[0].cityIdentity)
        assertEquals("Tirupati", result[0].displayName)
        assertTrue(result[0].featureFlags.allowProducts)
    }

    @Test
    fun `checkServiceability - matches by city identity`() {
        val tirupati = ServiceRegion(
            cityIdentity = "tirupati",
            displayName = "Tirupati",
            state = "Andhra Pradesh",
            centerLatitude = 13.6288,
            centerLongitude = 79.4192,
            radiusKm = 25.0,
            status = RegionStatus.ENABLED
        )
        whenever(serviceRegionRepository.findAllByStatusOrderBySortOrderAsc(RegionStatus.ENABLED)).thenReturn(listOf(tirupati))

        val check = serviceRegionService.checkServiceability(null, null, "tirupati", null)

        assertTrue(check.serviceable)
        assertNotNull(check.region)
        assertEquals("Tirupati", check.region?.displayName)
    }

    @Test
    fun `checkServiceability - rejects disabled or unserviceable region`() {
        whenever(serviceRegionRepository.findAllByStatusOrderBySortOrderAsc(RegionStatus.ENABLED)).thenReturn(emptyList())

        val check = serviceRegionService.checkServiceability(null, null, "unknown_city", null)

        assertFalse(check.serviceable)
        assertNull(check.region)
        assertNotNull(check.reason)
    }

    @Test
    fun `createRegion - saves lowercase cityIdentity and defaults`() {
        val req = CreateServiceRegionRequest(
            cityIdentity = "Vijayawada ",
            displayName = "Vijayawada",
            state = "Andhra Pradesh",
            centerLatitude = 16.5062,
            centerLongitude = 80.6480
        )

        whenever(serviceRegionRepository.save(any<ServiceRegion>())).thenAnswer { it.getArgument(0) }

        val created = serviceRegionService.createRegion(req)

        assertEquals("vijayawada", created.cityIdentity)
        assertEquals("Vijayawada", created.displayName)
        assertEquals(RegionStatus.ENABLED, created.status)
    }
}
