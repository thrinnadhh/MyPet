package com.pawsnearme.captainservice.controller

import com.pawsnearme.captainservice.model.CaptainProfile
import com.pawsnearme.captainservice.service.CaptainService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Resolves the current captain profile from gateway-authenticated identity. */
@RestController
@RequestMapping("/api/v1/captains")
class CurrentCaptainController(
    private val captainService: CaptainService
) {
    @GetMapping("/me")
    fun getCurrentCaptain(
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<CaptainProfile> {
        val captainId = parseUserId(xUserId)
        return ResponseEntity.ok(captainService.getProfile(captainId))
    }

    private fun parseUserId(raw: String?): UUID {
        if (raw.isNullOrBlank()) {
            throw CaptainController.CaptainAccessDeniedException("Unauthorized: user context missing")
        }
        return runCatching { UUID.fromString(raw) }
            .getOrElse {
                throw CaptainController.CaptainAccessDeniedException("Unauthorized: invalid user context")
            }
    }
}
