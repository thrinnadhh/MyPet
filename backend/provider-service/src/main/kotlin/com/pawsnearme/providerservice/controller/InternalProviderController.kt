package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.repository.AddressRepository
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.ProfileRepository
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

data class InternalProviderOperationalResponse(
    val providerId: UUID,
    val status: String,
    val operational: Boolean,
)

data class InternalDeliveryAddressResponse(
    val addressId: UUID,
    val customerId: UUID,
    val city: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
)

data class InternalCustomerPetIdentityResponse(
    val customerId: UUID,
    val customerName: String,
    val petId: UUID,
    val petName: String,
)

@RestController
@RequestMapping("/api/v1/internal/providers")
class InternalProviderController(
    private val providerRepository: ProviderRepository,
    private val profileRepository: ProfileRepository,
    private val petRepository: PetRepository,
    private val addressRepository: AddressRepository,
    @Value("\${internal.api.secret}") private val internalSecret: String
) {
    @GetMapping("/{id}/owner")
    fun getProviderOwner(
        @PathVariable id: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?
    ): ResponseEntity<InternalProviderOwnerResponse> {
        requireInternalSecret(providedSecret)
        val provider = providerRepository.findById(id)
            .orElseThrow { NoSuchElementException("Provider with ID $id not found") }
        return ResponseEntity.ok(
            InternalProviderOwnerResponse(
                providerId = requireNotNull(provider.providerId),
                ownerUserId = provider.ownerUserId
            )
        )
    }

    @GetMapping("/{id}/operational")
    fun getProviderOperationalState(
        @PathVariable id: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?
    ): ResponseEntity<InternalProviderOperationalResponse> {
        requireInternalSecret(providedSecret)
        val provider = providerRepository.findById(id)
            .orElseThrow { NoSuchElementException("Provider with ID $id not found") }
        return ResponseEntity.ok(
            InternalProviderOperationalResponse(
                providerId = requireNotNull(provider.providerId),
                status = provider.status.name,
                operational = provider.status.name == "ACTIVE",
            )
        )
    }

    @GetMapping("/customers/{customerId}/addresses/{addressId}")
    fun getCustomerDeliveryAddress(
        @PathVariable customerId: UUID,
        @PathVariable addressId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?
    ): ResponseEntity<InternalDeliveryAddressResponse> {
        requireInternalSecret(providedSecret)
        val address = addressRepository.findById(addressId)
            .orElseThrow { NoSuchElementException("Delivery address not found") }
        if (address.userId != customerId) {
            throw ProviderAccessDeniedException("Delivery address does not belong to the customer")
        }
        return ResponseEntity.ok(
            InternalDeliveryAddressResponse(
                addressId = requireNotNull(address.addressId),
                customerId = customerId,
                city = address.city,
                pincode = address.pincode,
                latitude = address.geoLat.toDouble(),
                longitude = address.geoLng.toDouble(),
            )
        )
    }

    @GetMapping("/customers/{customerId}/pets/{petId}/identity")
    fun getCustomerPetIdentity(
        @PathVariable customerId: UUID,
        @PathVariable petId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?
    ): ResponseEntity<InternalCustomerPetIdentityResponse> {
        requireInternalSecret(providedSecret)
        val profile = profileRepository.findById(customerId)
            .orElseThrow { NoSuchElementException("Customer profile not found") }
        val pet = petRepository.findById(petId)
            .orElseThrow { NoSuchElementException("Pet not found") }
        if (pet.ownerId != customerId) {
            throw ProviderAccessDeniedException("Pet does not belong to the appointment customer")
        }
        return ResponseEntity.ok(
            InternalCustomerPetIdentityResponse(
                customerId = customerId,
                customerName = profile.fullName,
                petId = petId,
                petName = pet.name,
            )
        )
    }

    private fun requireInternalSecret(providedSecret: String?) {
        if (
            internalSecret.isBlank() || providedSecret.isNullOrBlank() ||
            !MessageDigest.isEqual(providedSecret.toByteArray(), internalSecret.toByteArray())
        ) {
            throw ProviderAccessDeniedException("Forbidden")
        }
    }
}