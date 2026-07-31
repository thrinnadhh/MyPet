package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.ProviderResponse
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/providers")
class ProviderAdminDecisionController(
    private val providerRepository: ProviderRepository,
) {
    @PostMapping("/{providerId}/reject")
    @Transactional
    fun rejectProvider(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<ProviderResponse> {
        if (role != "ADMIN") {
            throw ProviderAccessDeniedException("Rejecting providers requires ADMIN role")
        }
        val provider = providerRepository.findById(providerId)
            .orElseThrow { NoSuchElementException("Provider not found") }
        if (provider.status != ProviderStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Only pending providers can be rejected")
        }
        provider.status = ProviderStatus.REJECTED
        val saved = providerRepository.save(provider)
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
}
