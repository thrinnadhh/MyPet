package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.CheckoutLocationPolicyService
import com.pawsnearme.orderservice.service.CheckoutQuoteRequest
import com.pawsnearme.orderservice.service.CheckoutQuoteResponse
import com.pawsnearme.orderservice.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutQuoteController(
    private val orderService: OrderService,
    private val checkoutLocationPolicyService: CheckoutLocationPolicyService,
) {

    @PostMapping("/quote")
    fun calculateQuote(
        @Valid @RequestBody request: CheckoutQuoteRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }
        val customerId = runCatching { UUID.fromString(authenticatedUserId) }.getOrNull()
            ?: return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Invalid authenticated user context."))

        val address = checkoutLocationPolicyService.requireAuthoritativeDeliveryLocation(
            customerId,
            request.deliveryAddressId,
        )
        val response = orderService.calculateQuote(
            request.copy(
                customerId = customerId,
                city = address.city,
                latitude = address.latitude,
                longitude = address.longitude,
            )
        )
        return ResponseEntity.ok(response)
    }
}
