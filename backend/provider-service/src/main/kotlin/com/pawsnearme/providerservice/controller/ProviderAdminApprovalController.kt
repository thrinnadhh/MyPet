package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.service.ProviderAdminApprovalService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/providers/admin")
class ProviderAdminApprovalController(
    private val approvalService: ProviderAdminApprovalService,
) {
    @PostMapping("/{providerId}/approve")
    fun approve(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<ProviderResponse> {
        val actorId = requireAdmin(userId, role)
        return ResponseEntity.ok(toResponse(approvalService.approve(providerId, actorId)))
    }

    private fun requireAdmin(userId: String?, role: String?): UUID {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required") }
    }

    private fun toResponse(p: Provider) = ProviderResponse(
        providerId = requireNotNull(p.providerId),
        ownerUserId = p.ownerUserId,
        providerType = p.providerType,
        fulfillmentType = p.fulfillmentType,
        name = p.name,
        description = p.description,
        licenseNumber = p.licenseNumber,
        licenseDocUrl = p.licenseDocUrl,
        addressLine = p.addressLine,
        city = p.city,
        pincode = p.pincode,
        longitude = p.geoLocation.x,
        latitude = p.geoLocation.y,
        status = p.status,
        ratingAvg = p.ratingAvg,
        ratingCount = p.ratingCount,
        commissionPct = p.commissionPct,
    )
}
