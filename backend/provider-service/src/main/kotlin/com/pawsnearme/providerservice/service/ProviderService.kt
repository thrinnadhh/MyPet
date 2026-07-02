package com.pawsnearme.providerservice.service

import com.pawsnearme.providerservice.model.*
import com.pawsnearme.providerservice.repository.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class ProviderService(
    private val providerRepository: ProviderRepository,
    private val providerDocumentRepository: ProviderDocumentRepository,
    private val profileRepository: ProfileRepository,
    private val userRoleJoinRepository: UserRoleJoinRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Transactional
    fun syncAuthenticatedProfile(
        userId: UUID,
        role: String?,
        email: String?,
        fullName: String?,
        phoneNumber: String?,
        avatarUrl: String?
    ): Profile {
        val normalizedRole = normalizeUserRole(role)
        val displayName = resolveDisplayName(userId, email, fullName)
        val resolvedPhone = phoneNumber?.takeIf { it.isNotBlank() } ?: "unspecified-$userId"

        val profile = profileRepository.findById(userId).orElse(null)?.apply {
            this.role = normalizedRole
            if (!fullName.isNullOrBlank()) this.fullName = fullName
            if (!phoneNumber.isNullOrBlank()) this.phoneNumber = phoneNumber
            if (!avatarUrl.isNullOrBlank()) this.avatarUrl = avatarUrl
        } ?: Profile(
            userId = userId,
            role = normalizedRole,
            fullName = displayName,
            phoneNumber = resolvedPhone,
            avatarUrl = avatarUrl
        )

        val savedProfile = profileRepository.save(profile)
        userRoleJoinRepository.save(UserRoleJoin(UserRoleKey(userId, normalizedRole)))
        return savedProfile
    }

    private fun normalizeUserRole(role: String?): UserRole {
        val normalized = role?.uppercase()
        return when (normalized) {
            "PROVIDER", "MERCHANT" -> UserRole.MERCHANT
            "CAPTAIN" -> UserRole.CAPTAIN
            "ADMIN" -> UserRole.ADMIN
            else -> UserRole.CUSTOMER
        }
    }

    private fun resolveDisplayName(userId: UUID, email: String?, fullName: String?): String {
        if (!fullName.isNullOrBlank()) return fullName
        val emailName = email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        return emailName ?: "User ${userId.toString().take(8)}"
    }

    @Transactional
    fun createProvider(
        ownerUserId: UUID,
        providerType: ProviderType,
        fulfillmentType: FulfillmentType,
        name: String,
        description: String?,
        licenseNumber: String?,
        licenseDocUrl: String?,
        addressLine: String,
        city: String,
        pincode: String,
        longitude: Double,
        latitude: Double
    ): Provider {
        // Validate user role is MERCHANT
        val profile = profileRepository.findById(ownerUserId).orElseThrow {
            IllegalArgumentException("Owner user profile not found: $ownerUserId")
        }
        if (profile.role != UserRole.MERCHANT) {
            throw IllegalArgumentException("User must have MERCHANT role to create a provider")
        }

        // Validate chk_fulfillment_matches_type
        validateFulfillmentType(providerType, fulfillmentType)

        // Validate VET_HOSPITAL requirements
        if (providerType == ProviderType.VET_HOSPITAL && licenseNumber.isNullOrBlank()) {
            throw IllegalArgumentException("Veterinary council license number is required for VET_HOSPITAL")
        }

        // longitude, latitude coordinate ordering for PostGIS
        val point = geometryFactory.createPoint(Coordinate(longitude, latitude))

        val provider = Provider(
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
            geoLocation = point,
            status = ProviderStatus.DRAFT
        )

        val savedProvider = providerRepository.save(provider)

        // Save license document if URL is provided
        if (!licenseDocUrl.isNullOrBlank()) {
            val docType = if (providerType == ProviderType.VET_HOSPITAL) "VET_LICENSE" else "BUSINESS_PROOF"
            providerDocumentRepository.save(
                ProviderDocument(
                    providerId = savedProvider.providerId!!,
                    docType = docType,
                    docUrl = licenseDocUrl
                )
            )
        }

        return savedProvider
    }

    private fun validateFulfillmentType(providerType: ProviderType, fulfillmentType: FulfillmentType) {
        val isValid = when (providerType) {
            ProviderType.PET_STORE -> fulfillmentType == FulfillmentType.DELIVERY
            ProviderType.VET_HOSPITAL, ProviderType.GROOMING_CENTER -> fulfillmentType == FulfillmentType.APPOINTMENT
        }
        if (!isValid) {
            throw IllegalArgumentException("Invalid combination: ProviderType $providerType does not support FulfillmentType $fulfillmentType")
        }
    }

    @Transactional
    fun uploadDocument(providerId: UUID, docType: String, docUrl: String): ProviderDocument {
        val provider = providerRepository.findById(providerId).orElseThrow {
            IllegalArgumentException("Provider not found: $providerId")
        }
        val document = ProviderDocument(
            providerId = provider.providerId!!,
            docType = docType,
            docUrl = docUrl
        )
        return providerDocumentRepository.save(document)
    }

    @Transactional
    fun submitForApproval(providerId: UUID): Provider {
        val provider = providerRepository.findById(providerId).orElseThrow {
            IllegalArgumentException("Provider not found: $providerId")
        }
        if (provider.status != ProviderStatus.DRAFT && provider.status != ProviderStatus.INFO_REQUESTED) {
            throw IllegalStateException("Provider must be in DRAFT or INFO_REQUESTED status to submit for approval")
        }

        provider.status = ProviderStatus.PENDING_APPROVAL
        return providerRepository.save(provider)
    }

    @Transactional
    fun approveProvider(providerId: UUID): Provider {
        val provider = providerRepository.findById(providerId).orElseThrow {
            IllegalArgumentException("Provider not found: $providerId")
        }
        if (provider.status != ProviderStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Provider must be in PENDING_APPROVAL status to approve")
        }

        provider.status = ProviderStatus.ACTIVE
        val approvedProvider = providerRepository.save(provider)

        // Publish ProviderApproved event to Kafka
        val event = mapOf(
            "event_id" to UUID.randomUUID().toString(),
            "event_type" to "ProviderApproved",
            "occurred_at" to Instant.now().toString(),
            "provider_id" to approvedProvider.providerId.toString(),
            "provider_type" to approvedProvider.providerType.name
        )
        
        kafkaTemplate.send("providers.events", approvedProvider.providerId.toString(), event)

        return approvedProvider
    }

    @Transactional
    fun updateCommission(providerId: UUID, commissionPct: BigDecimal, actorUserId: UUID?, reason: String?): Provider {
        if (commissionPct < BigDecimal.ZERO || commissionPct > BigDecimal("50.00")) {
            throw IllegalArgumentException("Commission percentage must be between 0 and 50")
        }

        val provider = providerRepository.findById(providerId).orElseThrow {
            IllegalArgumentException("Provider not found: $providerId")
        }
        val previousCommissionPct = provider.commissionPct
        provider.commissionPct = commissionPct.setScale(2, RoundingMode.HALF_UP)
        val updatedProvider = providerRepository.save(provider)

        val event = mapOf(
            "event_id" to UUID.randomUUID().toString(),
            "event_type" to "ProviderCommissionUpdated",
            "occurred_at" to Instant.now().toString(),
            "actor_id" to actorUserId?.toString(),
            "provider_id" to updatedProvider.providerId.toString(),
            "previous_commission_pct" to previousCommissionPct.toPlainString(),
            "new_commission_pct" to updatedProvider.commissionPct.toPlainString(),
            "reason" to reason
        )

        kafkaTemplate.send("providers.events", updatedProvider.providerId.toString(), event)

        return updatedProvider
    }

    @Transactional
    fun updateProviderRating(providerId: UUID, rating: Int): Provider {
        val provider = providerRepository.findById(providerId).orElseThrow {
            IllegalArgumentException("Provider not found: $providerId")
        }
        val currentCount = provider.ratingCount
        val currentAvg = provider.ratingAvg.toDouble()

        val newCount = currentCount + 1
        val newAvg = (currentAvg * currentCount + rating) / newCount

        provider.ratingCount = newCount
        provider.ratingAvg = java.math.BigDecimal.valueOf(newAvg).setScale(2, java.math.RoundingMode.HALF_UP)
        return providerRepository.save(provider)
    }
}
