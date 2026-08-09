package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Profile
import com.pawsnearme.providerservice.service.ProfileAdminAccessService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class AdminAccessChangeRequest(
    @field:Size(min = 3, max = 500)
    val reason: String,
)

@RestController
@RequestMapping("/api/v1/profiles/admin")
class ProfileAdminAccessController(
    private val accessService: ProfileAdminAccessService,
) {
    @PostMapping("/{userId}/revoke")
    fun revoke(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: AdminAccessChangeRequest,
        @RequestHeader("X-User-Id", required = false) actorUserId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<ProfileResponse> = ResponseEntity.ok(
        toResponse(accessService.revoke(userId, requireAdmin(actorUserId, role), request.reason)),
    )

    @PostMapping("/{userId}/restore")
    fun restore(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: AdminAccessChangeRequest,
        @RequestHeader("X-User-Id", required = false) actorUserId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<ProfileResponse> = ResponseEntity.ok(
        toResponse(accessService.restore(userId, requireAdmin(actorUserId, role), request.reason)),
    )

    private fun requireAdmin(userId: String?, role: String?): UUID {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required") }
    }

    private fun toResponse(profile: Profile) = ProfileResponse(
        userId = profile.userId,
        role = profile.role,
        fullName = profile.fullName,
        phoneNumber = profile.phoneNumber,
        avatarUrl = profile.avatarUrl,
        suspended = profile.suspended,
    )
}
