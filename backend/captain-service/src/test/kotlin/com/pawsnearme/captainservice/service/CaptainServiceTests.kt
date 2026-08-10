package com.pawsnearme.captainservice.service

import com.pawsnearme.captainservice.model.CaptainDocument
import com.pawsnearme.captainservice.model.CaptainProfile
import com.pawsnearme.captainservice.model.CaptainStatus
import com.pawsnearme.captainservice.model.VehicleType
import com.pawsnearme.captainservice.repository.CaptainDocumentRepository
import com.pawsnearme.captainservice.repository.CaptainEarningRepository
import com.pawsnearme.captainservice.repository.CaptainProfileRepository
import com.pawsnearme.captainservice.security.BankDataCipher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.GeoOperations
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import java.time.Duration
import java.util.Optional
import java.util.UUID

class CaptainServiceTests {

    private val profileRepository: CaptainProfileRepository = mock()
    private val earningRepository: CaptainEarningRepository = mock()
    private val geoOps: GeoOperations<String, String> = mock()
    private val zsetOps: ZSetOperations<String, String> = mock()
    private val setOps: SetOperations<String, String> = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private val redisTemplate: StringRedisTemplate = mock {
        on { opsForGeo() } doReturn geoOps
        on { opsForZSet() } doReturn zsetOps
        on { opsForSet() } doReturn setOps
        on { opsForValue() } doReturn valueOps
    }
    private val documentRepository: CaptainDocumentRepository = mock()
    private val bankDataCipher: BankDataCipher = mock()
    private val service = CaptainService(
        profileRepository,
        earningRepository,
        documentRepository,
        redisTemplate,
        bankDataCipher,
    )

    private val captainId = UUID.randomUUID()

    private fun activeProfile() = CaptainProfile(
        captainId = captainId,
        status = CaptainStatus.ACTIVE,
        vehicleType = VehicleType.BIKE
    )

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
    fun `toggleOnlineStatus - active captain creates online and fresh-location markers`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))
        whenever(geoOps.add(any(), any(), any<String>())).thenReturn(1L)

        val result = service.toggleOnlineStatus(captainId, true, 77.59, 12.97)

        assertEquals("ONLINE", result)
        verify(geoOps).add(any(), any(), eq(captainId.toString()))
        verify(setOps).add("captains:online", captainId.toString())
        verify(valueOps).set(
            eq("captains:location:fresh:$captainId"),
            eq("fresh"),
            eq(Duration.ofSeconds(60)),
        )
    }

    @Test
    fun `location update requires online captain and refreshes freshness TTL`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))
        whenever(setOps.isMember("captains:online", captainId.toString())).thenReturn(true)
        whenever(geoOps.add(any(), any(), any<String>())).thenReturn(1L)

        service.updateLocation(captainId, 77.59, 12.97)

        verify(geoOps).add(any(), any(), eq(captainId.toString()))
        verify(valueOps).set(
            eq("captains:location:fresh:$captainId"),
            eq("fresh"),
            eq(Duration.ofSeconds(60)),
        )
    }

    @Test
    fun `location update from offline captain is rejected`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))
        whenever(setOps.isMember("captains:online", captainId.toString())).thenReturn(false)

        val error = assertThrows<IllegalStateException> {
            service.updateLocation(captainId, 77.59, 12.97)
        }

        assertTrue(error.message!!.contains("must be online"))
    }

    @Test
    fun `toggleOnlineStatus - go offline - removes all dispatch eligibility markers`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.of(activeProfile()))

        val result = service.toggleOnlineStatus(captainId, false, null, null)

        assertEquals("OFFLINE", result)
        verify(zsetOps).remove(any(), eq(captainId.toString()))
        verify(setOps).remove("captains:online", captainId.toString())
        verify(redisTemplate).delete("captains:location:fresh:$captainId")
    }

    @Test
    fun `onboardCaptain encrypts bank fields before save and clears dispatch availability`() {
        whenever(profileRepository.findById(captainId)).thenReturn(Optional.empty())
        whenever(profileRepository.save(any())).thenAnswer { it.getArgument<CaptainProfile>(0) }
        whenever(documentRepository.save(any())).thenAnswer { it.getArgument<CaptainDocument>(0) }
        whenever(bankDataCipher.encrypt("1234567890")).thenReturn("v1:encrypted-account")
        whenever(bankDataCipher.encrypt("HDFC0001234")).thenReturn("v1:encrypted-ifsc")

        val result = service.onboardCaptain(
            captainId,
            VehicleType.BIKE,
            "KA01AB1234",
            null,
            "1234567890",
            "HDFC0001234",
            null,
            emptyList(),
        )

        assertEquals(CaptainStatus.PENDING_APPROVAL, result.status)
        assertEquals("v1:encrypted-account", result.bankAccount)
        assertEquals("v1:encrypted-ifsc", result.bankIfsc)
        verify(setOps).remove("captains:online", captainId.toString())
    }
}
