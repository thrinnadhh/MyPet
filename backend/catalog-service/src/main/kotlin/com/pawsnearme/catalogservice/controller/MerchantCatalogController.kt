package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.catalogservice.repository.ProviderRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class MerchantOfferingPage(
    val providerId: UUID,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val content: List<Offering>,
)

@RestController
@RequestMapping("/api/v1/catalog/merchant")
class MerchantCatalogController(
    private val offeringRepository: OfferingRepository,
    private val providerRepository: ProviderRepository,
) {
    @GetMapping("/offerings")
    fun listMerchantOfferings(
        @RequestParam providerId: UUID,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<MerchantOfferingPage> {
        val role = xUserRole?.uppercase()
        if (role != "MERCHANT" && role != "ADMIN") {
            throw CatalogAccessDeniedException("Merchant or admin role required")
        }
        if (role == "MERCHANT") {
            val requesterId = xUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw CatalogAccessDeniedException("Authenticated merchant identity required")
            if (!providerRepository.existsByProviderIdAndOwnerUserId(providerId, requesterId)) {
                throw CatalogAccessDeniedException("Access denied to another merchant's catalog")
            }
        }

        val boundedPage = page.coerceAtLeast(0)
        val boundedSize = size.coerceIn(1, 100)
        val normalizedQuery = query.trim().take(120)
        val pageable = PageRequest.of(
            boundedPage,
            boundedSize,
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("offeringId")),
        )
        val result = offeringRepository.searchMerchantOfferings(providerId, normalizedQuery, pageable)
        return ResponseEntity.ok(
            MerchantOfferingPage(
                providerId = providerId,
                page = boundedPage,
                size = boundedSize,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
                hasNext = result.hasNext(),
                content = result.content,
            )
        )
    }
}
