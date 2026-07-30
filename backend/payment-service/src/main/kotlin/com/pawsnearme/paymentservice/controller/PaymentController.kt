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
    @field:jakarta.validation.constraints.DecimalMin("0.01")
    @field:jakarta.validation.constraints.DecimalMax("10000000.00")
    val amount: BigDecimal,
    @field:jakarta.validation.constraints.Pattern(regexp = "ORDER_PAYMENT|APPOINTMENT_PAYMENT")
    val transactionType: String
)

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val paymentService: PaymentService) {

    @PostMapping("/orders")
    fun createRazorpayOrder(
        @Valid @RequestBody request: CreateRazorpayOrderRequest,
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
        @Valid @RequestBody request: PaymentResultRequest,
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
        @Valid @RequestBody request: com.pawsnearme.paymentservice.service.RegisterLinkedAccountRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<com.pawsnearme.paymentservice.model.LinkedAccount> {
        if (role !in setOf("ADMIN", "MERCHANT", "CAPTAIN")) {
            throw PaymentAccessDeniedException("Access denied for linked account registration")
        }
        if (role != "ADMIN" && userId != request.payeeUserId.toString()) {
            throw PaymentAccessDeniedException("Users can only register their own payout account")
        }
        if (role != "ADMIN" && role != request.payeeRole) {
            throw PaymentAccessDeniedException("Payout account role does not match authenticated role")
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

    @PostMapping("/promotions/reserve")
    fun reserveCoupon(
        @Valid @RequestBody req: com.pawsnearme.paymentservice.service.CouponReservationRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != req.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for coupon reservation")
        }
        val res = paymentService.reserveCoupon(req)
        return ResponseEntity.ok(res)
    }

    @PostMapping("/promotions/release")
    fun releaseCouponReservation(
        @RequestParam code: String,
        @RequestParam userId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for coupon release")
        }
        paymentService.releaseCouponReservation(code, userId, orderId)
        return ResponseEntity.ok(mapOf("status" to "released"))
    }

    @PostMapping("/promotions/redeem")
    fun redeemCouponReservation(
        @RequestParam code: String,
        @RequestParam userId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied for coupon redemption")
        }
        paymentService.redeemCouponReservation(code, userId, orderId)
        return ResponseEntity.ok(mapOf("status" to "redeemed"))
    }

    @GetMapping("/cod/config")
    fun getCodConfig(): ResponseEntity<Any> {
        return ResponseEntity.ok(paymentService.getCodConfig())
    }

    @PostMapping("/cod/config")
    fun updateCodConfig(
        @Valid @RequestBody req: com.pawsnearme.paymentservice.service.CodConfigRequest,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Any> {
        if (role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied: COD configuration requires ADMIN role")
        }
        return ResponseEntity.ok(paymentService.updateCodConfig(req))
    }

    @PostMapping("/cod/check")
    fun checkCodEligibility(
        @Valid @RequestBody req: com.pawsnearme.paymentservice.service.CodCheckRequest
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(paymentService.checkCodEligibility(req))
    }

    @ExceptionHandler(PaymentAccessDeniedException::class)
    fun handleAccessDenied(ex: PaymentAccessDeniedException): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
    }
}
