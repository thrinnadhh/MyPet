package com.pawsnearme.captainservice.service

import com.pawsnearme.captainservice.model.*
import com.pawsnearme.captainservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.redis.core.GeoOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.util.Optional
import java.util.UUID

class CaptainServiceTests {

    private val profileRepository: CaptainProfileRepository = mock()
    private val earningRepository: CaptainEarningRepository = mock()
    private val geoOps: GeoOperations<String, String> = mock()
    private val zsetOps: ZSetOperations<String, String> = mock()
    private val redisTemplate: StringRedisTemplate = mock {
        on { opsForGeo() } doReturn geoOps
        on { opsForZSet() } doReturn zsetOps
    }

    private val service = CaptainService(profileRepository, earningRepository, redisTemplate)

    private val captainId = UUID.randomUUID()

    private fun activeProfile() = CaptainProfile(
        captainId = captainId,
        status = CaptainStatus.ACTIVE,
        vehicleType = VehicleType.BIKE
    )

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    fun `getProfile - not found - throws NoSuchElementException`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.empty())
        assertThrows<NoSuchElementException> { service.getProfile(captainId) }
    }

    @Test
    fun `getProfile - found - returns profile`() {
        val profile = activeProfile()
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(profile))
        assertEquals(captainId, service.getProfile(captainId).captainId)
    }

    // ── toggleOnlineStatus ────────────────────────────────────────────────────

    @Test
    fun `toggleOnlineStatus - inactive captain - throws IllegalStateException`() {
        val inactiveProfile = activeProfile().apply { status = CaptainStatus.SUSPENDED }
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(inactiveProfile))

        val ex = assertThrows<IllegalStateException> {
            service.toggleOnlineStatus(captainId, true, 77.59, 12.97)
        }
        assertTrue(ex.message!!.contains("not active"))
    }

    @Test
    fun `toggleOnlineStatus - go online without coords - throws IllegalArgumentException`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))

        val ex = assertThrows<IllegalArgumentException> {
            service.toggleOnlineStatus(captainId, online = true, longitude = null, latitude = null)
        }
        assertTrue(ex.message!!.contains("Coordinates required"))
    }

    @Test
    fun `toggleOnlineStatus - go online with coords - adds to Redis Geo and returns ONLINE`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))
        whenever(geoOps.add(any(), any(), any<String>())).thenReturn(1L)

        val result = service.toggleOnlineStatus(captainId, true, 77.59, 12.97)
        assertEquals("ONLINE", result)
        verify(geoOps).add(any(), any(), eq(captainId.toString()))
    }

    @Test
    fun `toggleOnlineStatus - go offline - removes from Redis and returns OFFLINE`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))

        val result = service.toggleOnlineStatus(captainId, false, null, null)
        assertEquals("OFFLINE", result)
        verify(zsetOps).remove(any(), eq(captainId.toString()))
    }

    // ── onboardCaptain ────────────────────────────────────────────────────────

    @Test
    fun `onboardCaptain - saves and returns profile with ACTIVE status`() {
        whenever(profileRepository.save(any())).thenAnswer { it.getArgument<CaptainProfile>(0) }

        val result = service.onboardCaptain(captainId, VehicleType.BIKE, "KA01AB1234", null)
        assertEquals(CaptainStatus.ACTIVE, result.status)
    }
}
