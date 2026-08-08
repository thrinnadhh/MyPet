package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.service.ProviderService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class RejectProviderRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 500)
    val reason: String
)

@RestController
@RequestMapping("/api/v1/providers")
class ProviderAdminDecisionController(
    private val providerService: ProviderService,
) {
    @PostMapping("/{providerId}/reject")
    fun rejectProvider(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @Valid @RequestBody request: RejectProviderRequest,
    ): ResponseEntity<ProviderResponse> {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ProviderAccessDeniedException("Rejecting providers requires ADMIN role")
        }
        val actorId = parseActorId(userId)
        val saved = providerService.rejectProvider(providerId, actorId, request.reason)
        return ResponseEntity.ok(
            ProviderResponse(
                providerId = requireNotNull(saved.providerId),
                ownerUserId = saved.ownerUserId,
                providerType = saved.providerType,
                fulfillmentType = saved.fulfillmentType,
                name = saved.name,
                description = saved.description,
                licenseNumber = saved.licenseNumber,
                licenseDocUrl = saved.licenseDocUrl,
                addressLine = saved.addressLine,
                city = saved.city,
                pincode = saved.pincode,
                longitude = saved.geoLocation.x,
                latitude = saved.geoLocation.y,
                status = saved.status,
                ratingAvg = saved.ratingAvg,
                ratingCount = saved.ratingCount,
                commissionPct = saved.commissionPct,
            )
        )
    }

    private fun parseActorId(userId: String?): UUID {
        if (userId.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Valid authenticated administrator identity is required")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw ProviderAccessDeniedException("Valid authenticated administrator identity is required") }
    }
}
