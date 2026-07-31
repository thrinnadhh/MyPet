package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Resolves provider identity exclusively from the authenticated gateway context.
 * Mobile clients must not guess that a Supabase user ID is also a provider ID.
 */
@RestController
@RequestMapping("/api/v1/providers")
class CurrentProviderController(
    private val providerRepository: ProviderRepository
) {
    @GetMapping("/me")
    fun getCurrentProviders(
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<List<ProviderResponse>> {
        val ownerUserId = parseUserId(xUserId)
        return ResponseEntity.ok(
            providerRepository.findByOwnerUserId(ownerUserId).map(::toResponse)
        )
    }

    private fun parseUserId(raw: String?): UUID {
        if (raw.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }
        return runCatching { UUID.fromString(raw) }
            .getOrElse { throw ProviderAccessDeniedException("Unauthorized: invalid user context") }
    }

    private fun toResponse(provider: Provider) = ProviderResponse(
        providerId = requireNotNull(provider.providerId),
        ownerUserId = provider.ownerUserId,
        providerType = provider.providerType,
        fulfillmentType = provider.fulfillmentType,
        name = provider.name,
        description = provider.description,
        licenseNumber = provider.licenseNumber,
        licenseDocUrl = provider.licenseDocUrl,
        addressLine = provider.addressLine,
        city = provider.city,
        pincode = provider.pincode,
        longitude = provider.geoLocation.x,
        latitude = provider.geoLocation.y,
        status = provider.status,
        ratingAvg = provider.ratingAvg,
        ratingCount = provider.ratingCount,
        commissionPct = provider.commissionPct
    )
}
