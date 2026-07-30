package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.model.Promotion
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.service.PaymentResultRequest
import com.pawsnearme.paymentservice.service.PaymentService
import com.pawsnearme.paymentservice.service.RazorpayOrderResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class PaymentAccessDeniedException(message: String) : RuntimeException(message)

data class CreateRazorpayOrderRequest(
    val userId: UUID,
    val referenceId: UUID,
    val amount: BigDecimal,
    val transactionType: String
)

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val paymentService: PaymentService) {

    @PostMapping("/orders")
    fun createRazorpayOrder(
        @RequestBody request: CreateRazorpayOrderRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != request.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for order initiation")
        }
        val response = paymentService.createRazorpayOrder(
            request.userId,
            request.referenceId,
            request.amount,
            request.transactionType
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/transactions/result")
    fun recordPaymentResult(
        @RequestBody request: PaymentResultRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != request.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for payment result")
        }
        val response = paymentService.recordPaymentResult(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader("X-Razorpay-Signature", required = false) signature: String?
    ): ResponseEntity<Any> {
        if (signature.isNullOrBlank()) {
            throw IllegalArgumentException("Missing signature header")
        }
        paymentService.processWebhook(payload, signature)
        return ResponseEntity.ok(mapOf("status" to "processed"))
    }

    @GetMapping("/transactions/{id}")
    fun getTransaction(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Any> {
        val transaction = paymentService.getTransactionById(id)
        if (xUserRole != "ADMIN" && xUserId != transaction.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied")
        }
        return ResponseEntity.ok(transaction)
    }

    @PostMapping("/linked-accounts")
    fun registerLinkedAccount(
        @RequestBody request: com.pawsnearme.paymentservice.service.RegisterLinkedAccountRequest,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<com.pawsnearme.paymentservice.model.LinkedAccount> {
        if (role != "ADMIN" && role != "MERCHANT") {
            throw PaymentAccessDeniedException("Access denied for linked account registration")
        }
        val account = paymentService.registerLinkedAccount(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(account)
    }

    @PostMapping("/payouts/calculate")
    fun calculatePayouts(
        @RequestParam start: String,
        @RequestParam end: String,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<Payout>> {
        if (role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied")
        }
        val payouts = paymentService.calculatePayouts(LocalDate.parse(start), LocalDate.parse(end))
        return ResponseEntity.ok(payouts)
    }

    @GetMapping("/payouts/{id}")
    fun getPayoutById(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Payout> {
        val payout = paymentService.getPayoutById(id)
        if (xUserRole != "ADMIN" && xUserId != payout.payeeUserId.toString()) {
            throw PaymentAccessDeniedException("Access denied")
        }
        return ResponseEntity.ok(payout)
    }

    @GetMapping("/payouts/user/{userId}")
    fun getPayoutHistory(
        @PathVariable userId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<List<Payout>> {
        if (xUserRole != "ADMIN" && xUserId != userId.toString()) {
            throw PaymentAccessDeniedException("Access denied")
        }
        return ResponseEntity.ok(paymentService.getPayoutHistory(userId))
    }


    @PostMapping("/promotions")
    fun createPromotion(
        @Valid @RequestBody promo: Promotion,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<Any> {
        if (role != "MERCHANT" && role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied: role not authorized")
        }
        val created = paymentService.createPromotion(promo, role, userId?.let(UUID::fromString))
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
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
        val promo = paymentService.validateCoupon(code, orderValue, providerId, category)
        return ResponseEntity.ok(promo)
    }

    @PostMapping("/refund")
    fun refundPayment(
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Any> {
        if (role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied: refund requires ADMIN role")
        }
        val tx = paymentService.refundPayment(orderId)
        return ResponseEntity.ok(tx)
    }
}
