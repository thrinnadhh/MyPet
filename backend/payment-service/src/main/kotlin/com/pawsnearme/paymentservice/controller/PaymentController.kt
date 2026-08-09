package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.model.Promotion
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import com.pawsnearme.paymentservice.service.CashfreeRefundLifecycleService
import com.pawsnearme.paymentservice.service.CouponReservationLifecycleService
import com.pawsnearme.paymentservice.service.CreateCashfreeOrderRequest
import com.pawsnearme.paymentservice.service.PaymentResultRequest
import com.pawsnearme.paymentservice.service.PaymentService
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

class PaymentAccessDeniedException(message: String) : RuntimeException(message)

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val cashfreeGatewayService: CashfreeGatewayService,
    private val cashfreeRefundLifecycleService: CashfreeRefundLifecycleService,
    private val couponReservationLifecycleService: CouponReservationLifecycleService,
    @Value("\${internal.api.secret:}") private val internalSecret: String = "",
) {

    @PostMapping("/orders")
    fun createCashfreeOrder(
        @Valid @RequestBody request: CreateCashfreeOrderRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != request.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for order initiation")
        }
        if (request.transactionType != "ORDER_PAYMENT") {
            throw IllegalArgumentException("Order payment endpoint requires ORDER_PAYMENT")
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cashfreeGatewayService.createOrder(request))
    }

    @PostMapping("/appointments")
    fun createCashfreeAppointmentPayment(
        @Valid @RequestBody request: CreateCashfreeOrderRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != request.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for appointment payment initiation")
        }
        if (request.transactionType != "APPOINTMENT_PAYMENT") {
            throw IllegalArgumentException("Appointment payment endpoint requires APPOINTMENT_PAYMENT")
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cashfreeGatewayService.createOrder(request))
    }

    @PostMapping("/transactions/result")
    fun reconcilePaymentResult(
        @Valid @RequestBody request: PaymentResultRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != request.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for payment result")
        }
        val event = cashfreeGatewayService.reconcile(request.referenceId)
        if (event.actorId != request.userId || event.amount.compareTo(request.amount) != 0) {
            throw IllegalArgumentException("Payment result does not match the initiated transaction")
        }
        return ResponseEntity.ok(event)
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader("X-Webhook-Signature", required = false) signature: String?,
        @RequestHeader("X-Webhook-Timestamp", required = false) timestamp: String?,
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: String? = null,
    ): ResponseEntity<Any> {
        if (signature.isNullOrBlank()) throw IllegalArgumentException("Missing Cashfree signature header")
        if (timestamp.isNullOrBlank()) throw IllegalArgumentException("Missing Cashfree timestamp header")
        val isNew = cashfreeRefundLifecycleService.processWebhook(payload, signature, timestamp, idempotencyKey)
        return ResponseEntity.ok(mapOf("status" to if (isNew) "processed" else "already_processed"))
    }

    @GetMapping("/transactions/{id}")
    fun getTransaction(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
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
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<Any> {
        if (role !in setOf("ADMIN", "MERCHANT", "CAPTAIN")) {
            throw PaymentAccessDeniedException("Access denied for linked account registration")
        }
        if (role != "ADMIN" && userId != request.payeeUserId.toString()) {
            throw PaymentAccessDeniedException("Users can only register their own payout account")
        }
        if (role != "ADMIN" && role != request.payeeRole) {
            throw PaymentAccessDeniedException("Payout account role does not match authenticated role")
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            mapOf(
                "error" to "Cashfree Easy Split onboarding is not activated for this environment",
                "code" to "CASHFREE_EASY_SPLIT_NOT_ACTIVE",
                "action" to "Complete Cashfree marketplace activation and vendor KYC before enabling settlements",
            ),
        )
    }

    @PostMapping("/payouts/calculate")
    fun calculatePayouts(
        @RequestParam start: String,
        @RequestParam end: String,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<List<Payout>> {
        if (role != "ADMIN") throw PaymentAccessDeniedException("Access denied")
        return ResponseEntity.ok(paymentService.calculatePayouts(LocalDate.parse(start), LocalDate.parse(end)))
    }

    @GetMapping("/payouts/{id}")
    fun getPayoutById(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
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
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
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
        @RequestHeader("X-User-Id", required = false) userId: String?,
    ): ResponseEntity<Any> {
        if (role != "MERCHANT" && role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied: role not authorized")
        }
        val created = paymentService.createPromotion(promo, role, userId?.let(UUID::fromString))
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/promotions")
    fun listPromotions(@RequestParam(required = false) providerId: UUID?): ResponseEntity<List<Promotion>> =
        ResponseEntity.ok(paymentService.listPromotions(providerId))

    @GetMapping("/promotions/validate")
    fun validateCoupon(
        @RequestParam code: String,
        @RequestParam orderValue: BigDecimal,
        @RequestParam providerId: UUID,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<Any> = ResponseEntity.ok(
        paymentService.validateCoupon(code, orderValue, providerId, category),
    )

    /**
     * Compatibility route for order-service in distributed mode. It is deliberately
     * service-to-service only. Human Admin refund decisions must go through the order
     * dispute/cancellation domain actions so actor, reason and business side effects
     * are captured before the payment provider is invoked.
     */
    @PostMapping("/refund")
    fun refundPayment(
        @RequestParam orderId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
    ): ResponseEntity<Any> {
        requireTrustedOrderService(providedSecret, serviceName)
        return ResponseEntity.ok(cashfreeGatewayService.refundOrder(orderId))
    }

    @PostMapping("/refund/{orderId}/reconcile")
    fun reconcileRefund(
        @PathVariable orderId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) userId: String?,
    ): ResponseEntity<Any> {
        requireAdminActor(userId, role)
        return ResponseEntity.ok(cashfreeRefundLifecycleService.reconcileReference(orderId))
    }

    @PostMapping("/promotions/reserve")
    fun reserveCoupon(
        @Valid @RequestBody req: com.pawsnearme.paymentservice.service.CouponReservationRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != req.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for coupon reservation")
        }
        return ResponseEntity.ok(paymentService.reserveCoupon(req))
    }

    @PostMapping("/promotions/release")
    fun releaseCouponReservation(
        @RequestParam code: String,
        @RequestParam userId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN" && xUserId != userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for coupon release")
        }
        couponReservationLifecycleService.release(code, userId, orderId)
        return ResponseEntity.ok(mapOf("status" to "released"))
    }

    @PostMapping("/promotions/redeem")
    fun redeemCouponReservation(
        @RequestParam code: String,
        @RequestParam userId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<Any> {
        if (xUserRole != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied for coupon redemption")
        }
        paymentService.redeemCouponReservation(code, userId, orderId)
        return ResponseEntity.ok(mapOf("status" to "redeemed"))
    }

    @GetMapping("/cod/config")
    fun getCodConfig(): ResponseEntity<Any> = ResponseEntity.ok(paymentService.getCodConfig())

    @PostMapping("/cod/config")
    fun updateCodConfig(
        @Valid @RequestBody req: com.pawsnearme.paymentservice.service.CodConfigRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<Any> {
        if (role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied: COD configuration requires ADMIN role")
        }
        return ResponseEntity.ok(paymentService.updateCodConfig(req))
    }

    @PostMapping("/cod/check")
    fun checkCodEligibility(
        @Valid @RequestBody req: com.pawsnearme.paymentservice.service.CodCheckRequest,
    ): ResponseEntity<Any> = ResponseEntity.ok(paymentService.checkCodEligibility(req))

    private fun requireAdminActor(userId: String?, role: String?): UUID {
        if (role != "ADMIN") {
            throw PaymentAccessDeniedException("Access denied: refund operation requires ADMIN role")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw PaymentAccessDeniedException("Valid administrator identity is required") }
    }

    private fun requireTrustedOrderService(providedSecret: String?, serviceName: String?) {
        val validSecret = internalSecret.isNotBlank() &&
            !providedSecret.isNullOrBlank() &&
            MessageDigest.isEqual(providedSecret.toByteArray(), internalSecret.toByteArray())
        if (!validSecret || serviceName != "order-service") {
            throw PaymentAccessDeniedException("Trusted order-service identity required for refund execution")
        }
    }

    @ExceptionHandler(PaymentAccessDeniedException::class)
    fun handleAccessDenied(ex: PaymentAccessDeniedException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
}
