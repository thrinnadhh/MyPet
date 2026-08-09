package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.repository.ProviderRepository
import com.pawsnearme.providerservice.service.MerchantProviderProfileService
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class UpdateMerchantProviderProfileRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:Size(max = 2000)
    val description: String?,
    @field:NotBlank
    @field:Size(max = 300)
    val addressLine: String,
    @field:NotBlank
    @field:Size(max = 120)
    val city: String,
    @field:Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be a valid 6-digit Indian pincode")
    val pincode: String,
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double,
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double,
    @field:Pattern(regexp = "^\\+?[1-9][0-9]{7,14}$", message = "contactPhone must be a valid international phone number")
    val contactPhone: String?,
    @field:Email
    @field:Size(max = 254)
    val contactEmail: String?,
    val opensAt: LocalTime?,
    val closesAt: LocalTime?,
    val weeklyOffDays: Set<DayOfWeek> = emptySet(),
)

data class MerchantProviderProfileResponse(
    val providerId: UUID,
    val ownerUserId: UUID,
    val providerType: ProviderType,
    val fulfillmentType: FulfillmentType,
    val name: String,
    val description: String?,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val longitude: Double,
    val latitude: Double,
    val contactPhone: String?,
    val contactEmail: String?,
    val opensAt: LocalTime?,
    val closesAt: LocalTime?,
    val weeklyOffDays: List<DayOfWeek>,
    val status: ProviderStatus,
    val ratingAvg: BigDecimal,
    val ratingCount: Int,
    val commissionPct: BigDecimal,
)

@RestController
@RequestMapping("/api/v1/providers")
class MerchantProviderProfileController(
    private val merchantProviderProfileService: MerchantProviderProfileService,
    private val providerRepository: ProviderRepository,
) {
    /*
     * This two-segment path intentionally avoids the public single-provider
     * wildcard GET matcher used for customer-visible provider discovery.
     * The controller still enforces the MERCHANT role and owner identity.
     */
    @GetMapping("/me/profiles")
    fun getMerchantProfiles(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?,
    ): ResponseEntity<List<MerchantProviderProfileResponse>> {
        if (userRole != "MERCHANT") {
            throw ProviderAccessDeniedException("Access denied: merchant role required")
        }
        val actorId = parseUserId(userId)
        return ResponseEntity.ok(providerRepository.findByOwnerUserId(actorId).map(::toResponse))
    }

    @PatchMapping("/{id}/profile")
    fun updateMerchantProfile(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMerchantProviderProfileRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<MerchantProviderProfileResponse> {
        if (userRole != "MERCHANT") {
            throw ProviderAccessDeniedException("Access denied: merchant role required")
        }
        val actorId = parseUserId(userId)
        val provider = merchantProviderProfileService.updateProfile(
            providerId = id,
            actorUserId = actorId,
            name = request.name,
            description = request.description,
            addressLine = request.addressLine,
            city = request.city,
            pincode = request.pincode,
            longitude = request.longitude,
            latitude = request.latitude,
            contactPhone = request.contactPhone,
            contactEmail = request.contactEmail,
            opensAt = request.opensAt,
            closesAt = request.closesAt,
            weeklyOffDays = request.weeklyOffDays,
        )
        return ResponseEntity.ok(toResponse(provider))
    }

    private fun parseUserId(raw: String?): UUID {
        if (raw.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }
        return runCatching { UUID.fromString(raw) }
            .getOrElse { throw ProviderAccessDeniedException("Unauthorized: invalid user context") }
    }

    private fun toResponse(provider: Provider) = MerchantProviderProfileResponse(
        providerId = requireNotNull(provider.providerId),
        ownerUserId = provider.ownerUserId,
        providerType = provider.providerType,
        fulfillmentType = provider.fulfillmentType,
        name = provider.name,
        description = provider.description,
        addressLine = provider.addressLine,
        city = provider.city,
        pincode = provider.pincode,
        longitude = provider.geoLocation.x,
        latitude = provider.geoLocation.y,
        contactPhone = provider.contactPhone,
        contactEmail = provider.contactEmail,
        opensAt = provider.opensAt,
        closesAt = provider.closesAt,
        weeklyOffDays = provider.weeklyOffDays
            ?.split(',')
            ?.mapNotNull { raw -> runCatching { DayOfWeek.valueOf(raw) }.getOrNull() }
            ?: emptyList(),
        status = provider.status,
        ratingAvg = provider.ratingAvg,
        ratingCount = provider.ratingCount,
        commissionPct = provider.commissionPct,
    )
}
