package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.DeliveryContact
import com.pawsnearme.providerservice.repository.AddressRepository
import com.pawsnearme.providerservice.repository.DeliveryContactRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class UpsertDeliveryContactRequest(
    @field:Pattern(
        regexp = "^\\+91[6-9]\\d{9}$",
        message = "Enter a valid Indian mobile number in +91XXXXXXXXXX format"
    )
    val phoneNumber: String
)

data class DeliveryContactResponse(
    val addressId: UUID,
    val phoneNumber: String
)

@RestController
@RequestMapping("/api/v1/addresses")
class DeliveryContactController(
    private val addressRepository: AddressRepository,
    private val deliveryContactRepository: DeliveryContactRepository
) {
    @GetMapping("/{addressId}/contact")
    fun getContact(
        @PathVariable addressId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<DeliveryContactResponse> {
        val userId = requireUserId(xUserId)
        requireOwnedAddress(addressId, userId)
        val contact = deliveryContactRepository.findById(addressId)
            .orElseThrow { NoSuchElementException("No delivery contact found for address $addressId") }
        return ResponseEntity.ok(DeliveryContactResponse(contact.addressId, contact.phoneNumber))
    }

    @PutMapping("/{addressId}/contact")
    fun upsertContact(
        @PathVariable addressId: UUID,
        @Valid @RequestBody request: UpsertDeliveryContactRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<DeliveryContactResponse> {
        val userId = requireUserId(xUserId)
        requireOwnedAddress(addressId, userId)
        val existing = deliveryContactRepository.findById(addressId).orElse(null)
        val saved = deliveryContactRepository.save(
            existing?.apply {
                phoneNumber = request.phoneNumber
            } ?: DeliveryContact(
                addressId = addressId,
                userId = userId,
                phoneNumber = request.phoneNumber
            )
        )
        return ResponseEntity.ok(DeliveryContactResponse(saved.addressId, saved.phoneNumber))
    }

    private fun requireOwnedAddress(addressId: UUID, userId: UUID) {
        val address = addressRepository.findById(addressId)
            .orElseThrow { NoSuchElementException("Address $addressId not found") }
        if (address.userId != userId) {
            throw ProviderAccessDeniedException("Delivery contact can only be changed for your own address")
        }
    }

    private fun requireUserId(value: String?): UUID {
        if (value.isNullOrBlank()) throw ProviderAccessDeniedException("Unauthorized: user context missing")
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw ProviderAccessDeniedException("Unauthorized: invalid user context") }
    }
}
