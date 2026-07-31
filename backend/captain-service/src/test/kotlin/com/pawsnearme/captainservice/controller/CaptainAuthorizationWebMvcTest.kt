package com.pawsnearme.captainservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.captainservice.model.CaptainProfile
import com.pawsnearme.captainservice.model.CaptainStatus
import com.pawsnearme.captainservice.model.VehicleType
import com.pawsnearme.captainservice.security.LegacyBankDataMigration
import com.pawsnearme.captainservice.service.CaptainService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(controllers = [CaptainController::class])
class CaptainAuthorizationWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var captainService: CaptainService

    @MockBean
    private lateinit var legacyBankDataMigration: LegacyBankDataMigration

    private val captainId = UUID.randomUUID()
    private val otherCaptainId = UUID.randomUUID()

    private val sampleProfile = CaptainProfile(
        captainId = captainId,
        status = CaptainStatus.ACTIVE,
        vehicleType = VehicleType.BIKE
    )

    @Test
    fun `getProfile - matching captain ID returns 200`() {
        whenever(captainService.getProfile(captainId)).thenReturn(sampleProfile)

        mockMvc.perform(
            get("/api/v1/captains/profiles/$captainId")
                .header("X-User-Id", captainId.toString())
                .header("X-User-Role", "CAPTAIN")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `getProfile - ADMIN role returns 200`() {
        whenever(captainService.getProfile(captainId)).thenReturn(sampleProfile)

        mockMvc.perform(
            get("/api/v1/captains/profiles/$captainId")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `getProfile - mismatched captain ID returns 403`() {
        mockMvc.perform(
            get("/api/v1/captains/profiles/$captainId")
                .header("X-User-Id", otherCaptainId.toString())
                .header("X-User-Role", "CAPTAIN")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `listPending - ADMIN role returns 200`() {
        whenever(captainService.listPendingCaptains()).thenReturn(listOf(sampleProfile))

        mockMvc.perform(
            get("/api/v1/captains/pending")
                .header("X-User-Role", "ADMIN")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `listPending - CAPTAIN role returns 403`() {
        mockMvc.perform(
            get("/api/v1/captains/pending")
                .header("X-User-Role", "CAPTAIN")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `approve - ADMIN role succeeds with 200`() {
        whenever(captainService.approveCaptain(captainId)).thenReturn(sampleProfile)

        mockMvc.perform(
            post("/api/v1/captains/$captainId/approve")
                .header("X-User-Role", "ADMIN")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `approve - CAPTAIN role returns 403`() {
        mockMvc.perform(
            post("/api/v1/captains/$captainId/approve")
                .header("X-User-Role", "CAPTAIN")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `toggleOnline - mismatched captain ID returns 403`() {
        val request = CaptainController.StatusRequest(
            captainId = captainId,
            online = true,
            longitude = 77.59,
            latitude = 12.97
        )

        mockMvc.perform(
            put("/api/v1/captains/status")
                .header("X-User-Id", otherCaptainId.toString())
                .header("X-User-Role", "CAPTAIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `toggleOnline - matching captain ID returns 200`() {
        whenever(captainService.toggleOnlineStatus(eq(captainId), eq(true), any(), any()))
            .thenReturn("ONLINE")

        val request = CaptainController.StatusRequest(
            captainId = captainId,
            online = true,
            longitude = 77.59,
            latitude = 12.97
        )

        mockMvc.perform(
            put("/api/v1/captains/status")
                .header("X-User-Id", captainId.toString())
                .header("X-User-Role", "CAPTAIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }
}
