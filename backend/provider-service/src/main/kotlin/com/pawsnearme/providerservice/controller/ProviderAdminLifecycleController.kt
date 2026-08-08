package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.service.ProviderAdminLifecycleService
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

data class ProviderLifecycleReasonRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 500)
    val reason: String
)

@RestController
@RequestMapping("/api/v1/providers")
class ProviderAdminLifecycleController(
    private val lifecycleService: ProviderAdminLifecycleService
) {
    @PostMapping("/{providerId}/suspend")
    fun suspendProvider(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @Valid @RequestBody request: ProviderLifecycleReasonRequest
    ): ResponseEntity<ProviderResponse> {
        requireAdmin(role)
        val provider = lifecycleService.suspendProvider(providerId, requireActor(userId), request.reason)
        return ResponseEntity.ok(provider.toResponse())
    }

    @PostMapping("/{providerId}/reactivate")
    fun reactivateProvider(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @Valid @RequestBody request: ProviderLifecycleReasonRequest
    ): ResponseEntity<ProviderResponse> {
        requireAdmin(role)
        val provider = lifecycleService.reactivateProvider(providerId, requireActor(userId), request.reason)
        return ResponseEntity.ok(provider.toResponse())
    }

    private fun requireAdmin(role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ProviderAccessDeniedException("Provider lifecycle administration requires ADMIN role")
        }
    }

    private fun requireActor(userId: String?): UUID {
        if (userId.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Valid authenticated administrator identity is required")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw ProviderAccessDeniedException("Valid authenticated administrator identity is required") }
    }

    private fun com.pawsnearme.providerservice.model.Provider.toResponse() = ProviderResponse(
        providerId = requireNotNull(providerId),
        ownerUserId = ownerUserId,
        providerType = providerType,
        fulfillmentType = fulfillmentType,
        name = name,
        description = description,
        licenseNumber = licenseNumber,
        licenseDocUrl = licenseDocUrl,
        addressLine = addressLine,
        city = city,
        pincode = pincode,
        longitude = geoLocation.x,
        latitude = geoLocation.y,
        status = status,
        ratingAvg = ratingAvg,
        ratingCount = ratingCount,
        commissionPct = commissionPct
    )
}
