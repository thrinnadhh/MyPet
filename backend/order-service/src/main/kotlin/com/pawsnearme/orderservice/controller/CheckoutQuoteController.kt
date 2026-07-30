package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.CheckoutQuoteRequest
import com.pawsnearme.orderservice.service.CheckoutQuoteResponse
import com.pawsnearme.orderservice.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutQuoteController(
    private val orderService: OrderService
) {

    @PostMapping("/quote")
    fun calculateQuote(
        @Valid @RequestBody request: CheckoutQuoteRequest
    ): ResponseEntity<CheckoutQuoteResponse> {
        val response = orderService.calculateQuote(request)
        return ResponseEntity.ok(response)
    }
}
