package com.pawsnearme.captainservice.controller

import com.pawsnearme.captainservice.model.*
import com.pawsnearme.captainservice.service.CaptainService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/captains")
class CaptainController(private val captainService: CaptainService) {

    data class OnboardRequest(
        val captainId: UUID?,
        val vehicleType: VehicleType,
        val vehicleNumber: String?,
        val licenseDocUrl: String?
    )

    data class StatusRequest(
        val captainId: UUID?,
        val online: Boolean,
        val longitude: Double?,
        val latitude: Double?
    )

    data class LocationRequest(
        val captainId: UUID?,
        val longitude: Double,
        val latitude: Double
    )

    @PostMapping("/profiles")
    fun onboardCaptain(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestBody request: OnboardRequest
    ): ResponseEntity<CaptainProfile> {
        val captainId = xUserId?.let { UUID.fromString(it) } ?: request.captainId
            ?: throw IllegalArgumentException("Missing captain context / ID")
        val profile = captainService.onboardCaptain(
            captainId,
            request.vehicleType,
            request.vehicleNumber,
            request.licenseDocUrl
        )
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/profiles/{id}")
    fun getProfile(@PathVariable id: UUID): ResponseEntity<CaptainProfile> {
        val profile = captainService.getProfile(id)
        return ResponseEntity.ok(profile)
    }

    @PutMapping("/status")
    fun toggleOnline(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestBody request: StatusRequest
    ): ResponseEntity<Map<String, String>> {
        val captainId = xUserId?.let { UUID.fromString(it) } ?: request.captainId
            ?: throw IllegalArgumentException("Missing captain context / ID")
        val status = captainService.toggleOnlineStatus(captainId, request.online, request.longitude, request.latitude)
        return ResponseEntity.ok(mapOf("status" to status))
    }

    @PutMapping("/location")
    fun updateLocation(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestBody request: LocationRequest
    ): ResponseEntity<Map<String, String>> {
        val captainId = xUserId?.let { UUID.fromString(it) } ?: request.captainId
            ?: throw IllegalArgumentException("Missing captain context / ID")
        captainService.updateLocation(captainId, request.longitude, request.latitude)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS"))
    }

    @GetMapping("/{id}/earnings")
    fun getEarnings(@PathVariable id: UUID): ResponseEntity<List<CaptainEarning>> {
        return ResponseEntity.ok(captainService.getEarnings(id))
    }
}
