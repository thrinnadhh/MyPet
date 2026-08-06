package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Address
import com.pawsnearme.providerservice.repository.AddressRepository
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/addresses")
class DefaultAddressController(
    private val addressRepository: AddressRepository,
) {
    @PutMapping("/default")
    fun upsertDefaultAddress(
        @Valid @RequestBody request: CreateAddressRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
    ): ResponseEntity<AddressResponse> {
        if (xUserId.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }

        val userId = runCatching { UUID.fromString(xUserId) }
            .getOrElse { throw ProviderAccessDeniedException("Unauthorized: invalid user context") }
        val addresses = addressRepository.findByUserId(userId)
        val currentDefault = addresses.firstOrNull { it.isDefault }

        addresses.filter { it.isDefault && it.addressId != currentDefault?.addressId }.forEach {
            it.isDefault = false
            addressRepository.save(it)
        }

        val saved = addressRepository.save(
            currentDefault?.apply {
                label = request.label
                line1 = request.line1
                line2 = request.line2
                city = request.city
                state = request.state
                pincode = request.pincode
                geoLat = request.geoLat
                geoLng = request.geoLng
                isDefault = true
            } ?: Address(
                userId = userId,
                label = request.label,
                line1 = request.line1,
                line2 = request.line2,
                city = request.city,
                state = request.state,
                pincode = request.pincode,
                geoLat = request.geoLat,
                geoLng = request.geoLng,
                isDefault = true,
            ),
        )

        return ResponseEntity.ok(
            AddressResponse(
                addressId = requireNotNull(saved.addressId),
                userId = saved.userId,
                label = saved.label,
                line1 = saved.line1,
                line2 = saved.line2,
                city = saved.city,
                state = saved.state,
                pincode = saved.pincode,
                geoLat = saved.geoLat,
                geoLng = saved.geoLng,
                isDefault = saved.isDefault,
            ),
        )
    }
}
