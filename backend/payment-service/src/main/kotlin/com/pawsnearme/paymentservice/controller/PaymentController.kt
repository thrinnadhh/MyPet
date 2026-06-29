package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.model.Promotion
import com.pawsnearme.paymentservice.service.PaymentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val paymentService: PaymentService) {

    @PostMapping("/payouts/calculate")
    fun calculatePayouts(
        @RequestParam start: String,
        @RequestParam end: String,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<Payout>> {
        if (role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val payouts = paymentService.calculatePayouts(LocalDate.parse(start), LocalDate.parse(end))
        return ResponseEntity.ok(payouts)
    }

    @GetMapping("/payouts/user/{userId}")
    fun getPayoutHistory(
        @PathVariable userId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<List<Payout>> {
        if (xUserRole != "ADMIN" && xUserId != userId.toString()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(paymentService.getPayoutHistory(userId))
    }

    @PostMapping("/promotions")
    fun createPromotion(
        @Valid @RequestBody promo: Promotion,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Any> {
        if (role != "MERCHANT" && role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied: role not authorized"))
        }
        return try {
            val created = paymentService.createPromotion(promo, role)
            ResponseEntity.status(HttpStatus.CREATED).body(created)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/promotions")
    fun listPromotions(
        @RequestParam(required = false) providerId: UUID?
    ): ResponseEntity<List<Promotion>> {
        return ResponseEntity.ok(paymentService.listPromotions(providerId))
    }

    @GetMapping("/promotions/validate")
    fun validateCoupon(
        @RequestParam code: String,
        @RequestParam orderValue: BigDecimal,
        @RequestParam providerId: UUID,
        @RequestParam(required = false) category: String?
    ): ResponseEntity<Any> {
        return try {
            val promo = paymentService.validateCoupon(code, orderValue, providerId, category)
            ResponseEntity.ok(promo)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
