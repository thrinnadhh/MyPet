package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.CheckoutIntegrityService
import com.pawsnearme.orderservice.service.CheckoutQuoteRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutQuoteController(
    private val checkoutIntegrityService: CheckoutIntegrityService,
) {
    @PostMapping("/quote")
    fun calculateQuote(
        @Valid @RequestBody request: CheckoutQuoteRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        return ResponseEntity.ok(
            checkoutIntegrityService.calculateQuote(
                request.copy(customerId = UUID.fromString(authenticatedUserId))
            )
        )
    }
}