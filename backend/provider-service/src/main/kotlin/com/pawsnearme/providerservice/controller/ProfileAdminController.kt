package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.repository.ProfileRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class AdminProfilePageResponse(
    val content: List<ProfileResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@RestController
@RequestMapping("/api/v1/profiles/admin")
class ProfileAdminController(
    private val profileRepository: ProfileRepository
) {
    @GetMapping
    fun listProfiles(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AdminProfilePageResponse> {
        requireAdmin(userId, role)
        if (page < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater.")
        }
        if (size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100.")
        }

        val result = profileRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "userId"))
        )
        return ResponseEntity.ok(
            AdminProfilePageResponse(
                content = result.content.map {
                    ProfileResponse(
                        userId = it.userId,
                        role = it.role,
                        fullName = it.fullName,
                        phoneNumber = it.phoneNumber,
                        avatarUrl = it.avatarUrl,
                        suspended = it.suspended
                    )
                },
                page = result.number,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages
            )
        )
    }

    private fun requireAdmin(userId: String?, role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required.")
        }
        try {
            UUID.fromString(userId)
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required.")
        }
    }
}
