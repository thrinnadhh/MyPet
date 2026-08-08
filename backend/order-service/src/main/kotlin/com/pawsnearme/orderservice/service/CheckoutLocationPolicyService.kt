package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.DeliveryAddressSnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.orderservice.repository.ServiceAreaConfigRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Resolves checkout location from the authenticated customer's persisted address
 * instead of trusting client-supplied city/coordinates. Discovery remains the
 * city/region authority; AdminOperations service-area records act as an optional
 * pincode-level delivery override on top of that regional decision.
 */
@Service
class CheckoutLocationPolicyService(
    private val providerModule: ProviderModuleApi,
    private val serviceAreaRepository: ServiceAreaConfigRepository,
) {
    fun requireAuthoritativeDeliveryLocation(customerId: UUID, addressId: UUID): DeliveryAddressSnapshot {
        val address = providerModule.deliveryAddress(customerId, addressId)
            ?: throw IllegalArgumentException(
                "DELIVERY_ADDRESS_INVALID: Selected delivery address is missing or does not belong to the customer."
            )

        val override = serviceAreaRepository.findById(address.pincode.trim()).orElse(null)
        if (override != null) {
            if (!override.enabled) {
                throw IllegalArgumentException(
                    "UNSERVICEABLE_REGION: Delivery is disabled for pincode ${override.pincode}."
                )
            }
            if (!override.deliveryEnabled) {
                throw IllegalArgumentException(
                    "DELIVERY_DISABLED: Delivery is temporarily unavailable for pincode ${override.pincode}."
                )
            }
        }
        return address
    }
}
