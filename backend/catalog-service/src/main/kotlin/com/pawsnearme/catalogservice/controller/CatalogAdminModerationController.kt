package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.service.AdminOfferingPage
import com.pawsnearme.catalogservice.service.CatalogAdminModerationService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CatalogModerationRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 500)
    val reason: String
)

@RestController
@RequestMapping("/api/v1/catalog/admin/offerings")
class CatalogAdminModerationController(
    private val moderationService: CatalogAdminModerationService
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AdminOfferingPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(moderationService.listOfferings(page, size))
    }

    @PostMapping("/{offeringId}/disable")
    fun disable(
        @PathVariable offeringId: UUID,
        @Valid @RequestBody request: CatalogModerationRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Offering> {
        val actor = requireAdmin(userId, role)
        return ResponseEntity.ok(moderationService.disable(offeringId, actor, request.reason))
    }

    @PostMapping("/{offeringId}/restore")
    fun restore(
        @PathVariable offeringId: UUID,
        @Valid @RequestBody request: CatalogModerationRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Offering> {
        val actor = requireAdmin(userId, role)
        return ResponseEntity.ok(moderationService.restore(offeringId, actor, request.reason))
    }

    private fun requireAdmin(userId: String?, role: String?): UUID {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw CatalogAccessDeniedException("Catalog moderation requires ADMIN role")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw CatalogAccessDeniedException("Valid authenticated administrator identity is required") }
    }
}
