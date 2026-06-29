package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.*
import com.pawsnearme.providerservice.repository.*
import com.pawsnearme.providerservice.service.ProviderService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

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
    val avatarUrl: String?
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

// --- Controllers ---

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(
    private val profileRepository: ProfileRepository,
    private val userRoleJoinRepository: UserRoleJoinRepository
) {
    @PostMapping
    fun createProfile(@Valid @RequestBody request: CreateProfileRequest): ResponseEntity<ProfileResponse> {
        val profile = Profile(
            userId = request.userId,
            role = request.role,
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            avatarUrl = request.avatarUrl
        )
        val savedProfile = profileRepository.save(profile)
        
        // Populate user_roles join table
        val roleKey = UserRoleKey(request.userId, request.role)
        userRoleJoinRepository.save(UserRoleJoin(roleKey))
        
        return ResponseEntity.ok(
            ProfileResponse(
                savedProfile.userId,
                savedProfile.role,
                savedProfile.fullName,
                savedProfile.phoneNumber,
                savedProfile.avatarUrl
            )
        )
    }

    @GetMapping("/{id}")
    fun getProfile(@PathVariable id: UUID): ResponseEntity<ProfileResponse> {
        val profile = profileRepository.findById(id)
        return if (profile.isPresent) {
            val p = profile.get()
            ResponseEntity.ok(ProfileResponse(p.userId, p.role, p.fullName, p.phoneNumber, p.avatarUrl))
        } else {
            ResponseEntity.notFound().build()
        }
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
        return try {
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
            ResponseEntity.ok(mapToResponse(provider))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}")
    fun getProvider(@PathVariable id: UUID): ResponseEntity<ProviderResponse> {
        val provider = providerRepository.findById(id)
        return if (provider.isPresent) {
            ResponseEntity.ok(mapToResponse(provider.get()))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{id}/documents")
    fun uploadDocument(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UploadDocumentRequest
    ): ResponseEntity<Any> {
        return try {
            val doc = providerService.uploadDocument(id, request.docType, request.docUrl)
            ResponseEntity.ok(doc)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/submit")
    fun submitForApproval(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            val provider = providerService.submitForApproval(id)
            ResponseEntity.ok(mapToResponse(provider))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/approve")
    fun approveProvider(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        if (userRole != "ADMIN") {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access Denied: Only administrators can approve providers."))
        }
        return try {
            val provider = providerService.approveProvider(id)
            ResponseEntity.ok(mapToResponse(provider))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
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
