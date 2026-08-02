package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.repository.ProviderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

data class InternalProviderOwnerResponse(
    val providerId: UUID,
    val ownerUserId: UUID
)

@RestController
@RequestMapping("/api/v1/internal/providers")
class InternalProviderController(
    private val providerRepository: ProviderRepository,
    @Value("\${internal.api.secret}") private val internalSecret: String
) {
    @GetMapping("/{id}/owner")
    fun getProviderOwner(
        @PathVariable id: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?
    ): ResponseEntity<InternalProviderOwnerResponse> {
        if (internalSecret.isBlank() || providedSecret.isNullOrBlank() ||
            !MessageDigest.isEqual(providedSecret.toByteArray(), internalSecret.toByteArray())
        ) {
            throw ProviderAccessDeniedException("Forbidden")
        }
        val provider = providerRepository.findById(id)
            .orElseThrow { NoSuchElementException("Provider with ID $id not found") }
        return ResponseEntity.ok(
            InternalProviderOwnerResponse(
                providerId = requireNotNull(provider.providerId),
                ownerUserId = provider.ownerUserId
            )
        )
    }
}
