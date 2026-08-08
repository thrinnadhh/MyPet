package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.repository.ProviderRepository
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

data class AdminProviderPageResponse(
    val content: List<ProviderResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@RestController
@RequestMapping("/api/v1/providers/admin")
class ProviderAdminQueryController(
    private val providerRepository: ProviderRepository
) {
    @GetMapping
    fun listProviders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: ProviderStatus?,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AdminProviderPageResponse> {
        requireAdmin(userId, role)
        if (page < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater")
        if (size !in 1..100) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100")

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "providerId"))
        val result = if (status == null) {
            providerRepository.findAll(pageable)
        } else {
            providerRepository.findByStatus(status, pageable)
        }
        return ResponseEntity.ok(
            AdminProviderPageResponse(
                content = result.content.map(::toResponse),
                page = result.number,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages
            )
        )
    }

    private fun requireAdmin(userId: String?, role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required")
        }
        if (runCatching { UUID.fromString(userId) }.getOrNull() == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required")
        }
    }

    private fun toResponse(p: com.pawsnearme.providerservice.model.Provider) = ProviderResponse(
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
        commissionPct = p.commissionPct
    )
}
