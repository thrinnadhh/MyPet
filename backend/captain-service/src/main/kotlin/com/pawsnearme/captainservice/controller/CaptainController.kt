package com.pawsnearme.captainservice.controller

import com.pawsnearme.captainservice.model.*
import com.pawsnearme.captainservice.service.CaptainService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/captains")
class CaptainController(private val captainService: CaptainService) {

    data class DocumentUpload(val docType: String, val docUrl: String)

    data class OnboardRequest(
        val captainId: UUID?,
        val vehicleType: VehicleType,
        val vehicleNumber: String?,
        val licenseDocUrl: String?,
        val bankAccount: String?,
        val bankIfsc: String?,
        val selfieDocUrl: String?,
        val documents: List<DocumentUpload> = emptyList(),
    )

    data class StatusRequest(
        val captainId: UUID?,
        val online: Boolean,
        val longitude: Double?,
        val latitude: Double?,
    )

    data class LocationRequest(
        val captainId: UUID?,
        val longitude: Double,
        val latitude: Double,
    )

    @PostMapping("/profiles")
    fun onboardCaptain(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestBody request: OnboardRequest,
    ): ResponseEntity<CaptainProfile> {
        val captainId = xUserId?.let { UUID.fromString(it) } ?: request.captainId
            ?: throw IllegalArgumentException("Missing captain context / ID")
        val profile = captainService.onboardCaptain(
            captainId,
            request.vehicleType,
            request.vehicleNumber,
            request.licenseDocUrl,
            request.bankAccount,
            request.bankIfsc,
            request.selfieDocUrl,
            request.documents.map { it.docType to it.docUrl },
        )
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/profiles/{id}")
    fun getProfile(@PathVariable id: UUID): ResponseEntity<CaptainProfile> =
        ResponseEntity.ok(captainService.getProfile(id))

    @GetMapping("/pending")
    fun listPending(): ResponseEntity<List<CaptainProfile>> =
        ResponseEntity.ok(captainService.listPendingCaptains())

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID): ResponseEntity<CaptainProfile> =
        ResponseEntity.ok(captainService.approveCaptain(id))

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: UUID): ResponseEntity<CaptainProfile> =
        ResponseEntity.ok(captainService.rejectCaptain(id))

    @GetMapping("/{id}/documents")
    fun listDocuments(@PathVariable id: UUID): ResponseEntity<List<CaptainDocument>> =
        ResponseEntity.ok(captainService.getDocuments(id))

    @PutMapping("/status")
    fun toggleOnline(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestBody request: StatusRequest,
    ): ResponseEntity<Map<String, String>> {
        val captainId = xUserId?.let { UUID.fromString(it) } ?: request.captainId
            ?: throw IllegalArgumentException("Missing captain context / ID")
        val status = captainService.toggleOnlineStatus(captainId, request.online, request.longitude, request.latitude)
        return ResponseEntity.ok(mapOf("status" to status))
    }

    @PutMapping("/location")
    fun updateLocation(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestBody request: LocationRequest,
    ): ResponseEntity<Map<String, String>> {
        val captainId = xUserId?.let { UUID.fromString(it) } ?: request.captainId
            ?: throw IllegalArgumentException("Missing captain context / ID")
        captainService.updateLocation(captainId, request.longitude, request.latitude)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS"))
    }

    @GetMapping("/{id}/earnings")
    fun getEarnings(@PathVariable id: UUID): ResponseEntity<List<CaptainEarning>> =
        ResponseEntity.ok(captainService.getEarnings(id))
}
