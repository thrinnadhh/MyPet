package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.*
import com.pawsnearme.providerservice.repository.*
import com.pawsnearme.providerservice.service.ProviderService
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

class ProviderAccessDeniedException(message: String) : RuntimeException(message)

// --- DTOs ---

data class CreateProfileRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val role: UserRole,
    @field:NotBlank val fullName: String,
    @field:NotBlank val phoneNumber: String,
    val avatarUrl: String?
)

data class ProfileResponse(
    val userId: UUID,
    val role: UserRole,
    val fullName: String,
    val phoneNumber: String,
    val avatarUrl: String?,
    val suspended: Boolean = false
)

data class CreateAddressRequest(
    val label: String?,
    @field:NotBlank val line1: String,
    val line2: String?,
    @field:NotBlank val city: String,
    @field:NotBlank val state: String,
    @field:NotBlank val pincode: String,
    @field:NotNull val geoLat: BigDecimal,
    @field:NotNull val geoLng: BigDecimal,
    val isDefault: Boolean = false
)

data class AddressResponse(
    val addressId: UUID,
    val userId: UUID,
    val label: String?,
    val line1: String,
    val line2: String?,
    val city: String,
    val state: String,
    val pincode: String,
    val geoLat: BigDecimal,
    val geoLng: BigDecimal,
    val isDefault: Boolean
)

data class CreateProviderRequest(
    @field:NotNull val ownerUserId: UUID,
    @field:NotNull val providerType: ProviderType,
    @field:NotNull val fulfillmentType: FulfillmentType,
    @field:NotBlank val name: String,
    val description: String?,
    val licenseNumber: String?,
    val licenseDocUrl: String?,
    @field:NotBlank val addressLine: String,
    @field:NotBlank val city: String,
    @field:NotBlank val pincode: String,
    @field:NotNull val longitude: Double,
    @field:NotNull val latitude: Double
)

data class ProviderResponse(
    val providerId: UUID,
    val ownerUserId: UUID,
    val providerType: ProviderType,
    val fulfillmentType: FulfillmentType,
    val name: String,
    val description: String?,
    val licenseNumber: String?,
    val licenseDocUrl: String?,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val longitude: Double,
    val latitude: Double,
    val status: ProviderStatus,
    val ratingAvg: BigDecimal,
    val ratingCount: Int,
    val commissionPct: BigDecimal
)

data class UploadDocumentRequest(
    @field:NotBlank val docType: String,
    @field:NotBlank val docUrl: String
)

data class UpdateProviderCommissionRequest(
    @field:NotNull
    @field:DecimalMin("0.00")
    @field:DecimalMax("50.00")
    val commissionPct: BigDecimal,
    val reason: String?
)

// --- Controllers ---

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(
    private val profileRepository: ProfileRepository,
    private val providerService: ProviderService
) {
    @PostMapping
    fun createProfile(@Valid @RequestBody request: CreateProfileRequest): ResponseEntity<ProfileResponse> {
        val savedProfile = providerService.syncAuthenticatedProfile(
            userId = request.userId,
            role = request.role.name,
            email = null,
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            avatarUrl = request.avatarUrl
        )

        return ResponseEntity.ok(
            ProfileResponse(
                savedProfile.userId,
                savedProfile.role,
                savedProfile.fullName,
                savedProfile.phoneNumber,
                savedProfile.avatarUrl,
                savedProfile.suspended
            )
        )
    }

    @PostMapping("/sync")
    fun syncAuthenticatedProfile(
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
        @RequestHeader("X-User-Email", required = false) xUserEmail: String?,
        @RequestHeader("X-User-Full-Name", required = false) xUserFullName: String?,
        @RequestHeader("X-User-Phone", required = false) xUserPhone: String?
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }

        val savedProfile = providerService.syncAuthenticatedProfile(
            userId = UUID.fromString(xUserId),
            role = xUserRole,
            email = xUserEmail,
            fullName = xUserFullName,
            phoneNumber = xUserPhone,
            avatarUrl = null
        )
        if (savedProfile.suspended) {
            throw ProviderAccessDeniedException("Access Denied: User access has been revoked.")
        }
        return ResponseEntity.ok(
            ProfileResponse(
                savedProfile.userId,
                savedProfile.role,
                savedProfile.fullName,
                savedProfile.phoneNumber,
                savedProfile.avatarUrl,
                savedProfile.suspended
            )
        )
    }

    @GetMapping("/{id}")
    fun getProfile(@PathVariable id: UUID): ResponseEntity<ProfileResponse> {
        val p = profileRepository.findById(id)
            .orElseThrow { NoSuchElementException("Profile with ID $id not found") }
        if (p.suspended) {
            throw ProviderAccessDeniedException("Access Denied: User access has been revoked.")
        }
        return ResponseEntity.ok(ProfileResponse(p.userId, p.role, p.fullName, p.phoneNumber, p.avatarUrl, p.suspended))
    }

    @GetMapping
    fun getAllProfiles(
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<List<ProfileResponse>> {
        if (userRole != "ADMIN") {
            throw ProviderAccessDeniedException("Access Denied: Only administrators can view all profiles.")
        }
        val profiles = profileRepository.findAll()
        return ResponseEntity.ok(profiles.map { ProfileResponse(it.userId, it.role, it.fullName, it.phoneNumber, it.avatarUrl, it.suspended) })
    }

    @PostMapping("/{id}/revoke")
    fun revokeAccess(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        if (userRole != "ADMIN") {
            throw ProviderAccessDeniedException("Access Denied: Only administrators can revoke user access.")
        }
        val p = profileRepository.findById(id)
            .orElseThrow { NoSuchElementException("Profile with ID $id not found") }
        p.suspended = true
        profileRepository.save(p)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Access revoked for user ${p.fullName}"))
    }

    @PostMapping("/{id}/restore")
    fun restoreAccess(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        if (userRole != "ADMIN") {
            throw ProviderAccessDeniedException("Access Denied: Only administrators can restore user access.")
        }
        val p = profileRepository.findById(id)
            .orElseThrow { NoSuchElementException("Profile with ID $id not found") }
        p.suspended = false
        profileRepository.save(p)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Access restored for user ${p.fullName}"))
    }
}

@RestController
@RequestMapping("/api/v1/addresses")
class AddressController(private val addressRepository: AddressRepository) {
    @PostMapping
    fun createAddress(
        @Valid @RequestBody request: CreateAddressRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId == null) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }

        val userId = UUID.fromString(xUserId)
        val existing = addressRepository.findByUserId(userId)
        val shouldBeDefault = request.isDefault || existing.isEmpty()
        if (shouldBeDefault) {
            existing.filter { it.isDefault }.forEach {
                it.isDefault = false
                addressRepository.save(it)
            }
        }

        val saved = addressRepository.save(
            Address(
                userId = userId,
                label = request.label,
                line1 = request.line1,
                line2 = request.line2,
                city = request.city,
                state = request.state,
                pincode = request.pincode,
                geoLat = request.geoLat,
                geoLng = request.geoLng,
                isDefault = shouldBeDefault
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(saved))
    }

    @GetMapping
    fun listAddresses(@RequestHeader("X-User-Id", required = false) xUserId: String?): ResponseEntity<Any> {
        if (xUserId == null) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }
        val userId = UUID.fromString(xUserId)
        return ResponseEntity.ok(addressRepository.findByUserId(userId).map { mapToResponse(it) })
    }

    @GetMapping("/default")
    fun getDefaultAddress(@RequestHeader("X-User-Id", required = false) xUserId: String?): ResponseEntity<Any> {
        if (xUserId == null) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }
        val userId = UUID.fromString(xUserId)
        val defaultAddress = addressRepository.findFirstByUserIdAndIsDefaultTrue(userId)
            ?: throw NoSuchElementException("No default delivery address found")
        return ResponseEntity.ok(mapToResponse(defaultAddress))
    }

    private fun mapToResponse(address: Address): AddressResponse {
        return AddressResponse(
            addressId = address.addressId!!,
            userId = address.userId,
            label = address.label,
            line1 = address.line1,
            line2 = address.line2,
            city = address.city,
            state = address.state,
            pincode = address.pincode,
            geoLat = address.geoLat,
            geoLng = address.geoLng,
            isDefault = address.isDefault
        )
    }
}

@RestController
@RequestMapping("/api/v1/providers")
class ProviderController(
    private val providerService: ProviderService,
    private val providerRepository: ProviderRepository
) {
    @PostMapping
    fun createProvider(@Valid @RequestBody request: CreateProviderRequest): ResponseEntity<Any> {
        val provider = providerService.createProvider(
            ownerUserId = request.ownerUserId,
            providerType = request.providerType,
            fulfillmentType = request.fulfillmentType,
            name = request.name,
            description = request.description,
            licenseNumber = request.licenseNumber,
            licenseDocUrl = request.licenseDocUrl,
            addressLine = request.addressLine,
            city = request.city,
            pincode = request.pincode,
            longitude = request.longitude,
            latitude = request.latitude
        )
        return ResponseEntity.ok(mapToResponse(provider))
    }

    @GetMapping("/pending")
    fun getPendingProviders(): ResponseEntity<List<ProviderResponse>> {
        val all = providerRepository.findAll()
        val pending = all.filter { it.status == ProviderStatus.PENDING_APPROVAL }
        return ResponseEntity.ok(pending.map { mapToResponse(it) })
    }

    @GetMapping("/{id}")
    fun getProvider(@PathVariable id: UUID): ResponseEntity<ProviderResponse> {
        val provider = providerRepository.findById(id)
            .orElseThrow { NoSuchElementException("Provider with ID $id not found") }
        return ResponseEntity.ok(mapToResponse(provider))
    }

    @PostMapping("/{id}/documents")
    fun uploadDocument(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UploadDocumentRequest
    ): ResponseEntity<Any> {
        val doc = providerService.uploadDocument(id, request.docType, request.docUrl)
        return ResponseEntity.ok(doc)
    }

    @PostMapping("/{id}/submit")
    fun submitForApproval(@PathVariable id: UUID): ResponseEntity<Any> {
        val provider = providerService.submitForApproval(id)
        return ResponseEntity.ok(mapToResponse(provider))
    }

    @PostMapping("/{id}/approve")
    fun approveProvider(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        if (userRole != "ADMIN") {
            throw ProviderAccessDeniedException("Access Denied: Only administrators can approve providers.")
        }
        val provider = providerService.approveProvider(id)
        return ResponseEntity.ok(mapToResponse(provider))
    }

    @PatchMapping("/{id}/commission")
    fun updateCommission(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) userRole: String?,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @Valid @RequestBody request: UpdateProviderCommissionRequest
    ): ResponseEntity<Any> {
        if (userRole != "ADMIN") {
            throw ProviderAccessDeniedException("Access Denied: Only administrators can update provider commission.")
        }
        val actorUserId = userId?.let { UUID.fromString(it) }
        val provider = providerService.updateCommission(id, request.commissionPct, actorUserId, request.reason)
        return ResponseEntity.ok(mapToResponse(provider))
    }

    @GetMapping
    fun getProvidersByOwner(@RequestParam ownerUserId: UUID): ResponseEntity<List<ProviderResponse>> {
        val providers = providerRepository.findByOwnerUserId(ownerUserId)
        return ResponseEntity.ok(providers.map { mapToResponse(it) })
    }

    private fun mapToResponse(p: Provider): ProviderResponse {
        return ProviderResponse(
            providerId = p.providerId!!,
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
}
