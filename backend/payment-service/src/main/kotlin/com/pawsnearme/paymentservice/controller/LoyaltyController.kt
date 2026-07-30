package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.model.LoyaltyProgram
import com.pawsnearme.paymentservice.model.LoyaltyRewardInstance
import com.pawsnearme.paymentservice.service.LoyaltyProgressResponse
import com.pawsnearme.paymentservice.service.LoyaltyService
import com.pawsnearme.paymentservice.service.LoyaltyWalletRewardDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class OrderDeliveredEventPayload(
    val orderId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val netAmount: BigDecimal
)

data class OrderRefundedEventPayload(
    val orderId: UUID,
    val customerId: UUID,
    val providerId: UUID
)

data class ReserveRewardRequest(
    val code: String,
    val providerId: UUID,
    val orderId: UUID? = null
)

@RestController
@RequestMapping("/api/v1/loyalty")
class LoyaltyController(
    private val loyaltyService: LoyaltyService
) {

    @PostMapping("/welcome-star/claim")
    fun claimWelcomeStar(
        @RequestParam providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyProgressResponse> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context for welcome star claim")
        }
        val customerId = UUID.fromString(xUserId)
        val progress = loyaltyService.claimWelcomeStar(customerId, providerId)
        return ResponseEntity.ok(progress)
    }

    @GetMapping("/progress")
    fun getProgress(
        @RequestParam providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyProgressResponse> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        return ResponseEntity.ok(loyaltyService.getProgress(customerId, providerId))
    }

    @GetMapping("/wallet")
    fun getWallet(
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<List<LoyaltyWalletRewardDto>> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        return ResponseEntity.ok(loyaltyService.getCustomerWallet(customerId))
    }

    @GetMapping("/ledger")
    fun getLedger(
        @RequestParam(required = false) providerId: UUID?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<List<LoyaltyLedgerEntry>> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        return ResponseEntity.ok(loyaltyService.getLedgerHistory(customerId, providerId))
    }

    @PostMapping("/rewards/reserve")
    fun reserveReward(
        @RequestBody req: ReserveRewardRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyRewardInstance> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        val reward = loyaltyService.reserveReward(req.code, customerId, req.providerId, req.orderId)
        return ResponseEntity.ok(reward)
    }

    @PostMapping("/rewards/release")
    fun releaseReward(
        @RequestParam code: String,
        @RequestParam(required = false) orderId: UUID?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        loyaltyService.releaseRewardReservation(code, customerId, orderId)
        return ResponseEntity.ok(mapOf("status" to "released"))
    }

    @PostMapping("/rewards/redeem")
    fun redeemReward(
        @RequestParam code: String,
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyRewardInstance> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        val reward = loyaltyService.redeemReward(code, customerId, orderId)
        return ResponseEntity.ok(reward)
    }

    @PostMapping("/events/order-delivered")
    fun handleOrderDelivered(
        @RequestBody payload: OrderDeliveredEventPayload
    ): ResponseEntity<Any> {
        val processed = loyaltyService.processOrderDeliveredEvent(
            payload.orderId,
            payload.customerId,
            payload.providerId,
            payload.netAmount
        )
        return ResponseEntity.ok(mapOf("processed" to processed))
    }

    @PostMapping("/events/order-refunded")
    fun handleOrderRefunded(
        @RequestBody payload: OrderRefundedEventPayload
    ): ResponseEntity<Any> {
        val processed = loyaltyService.processOrderRefundEvent(
            payload.orderId,
            payload.customerId,
            payload.providerId
        )
        return ResponseEntity.ok(mapOf("processed" to processed))
    }

    @PostMapping("/reconcile")
    fun reconcileAccount(
        @RequestParam providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Missing authenticated user context")
        }
        val customerId = UUID.fromString(xUserId)
        val balance = loyaltyService.reconcileAccountFromLedger(customerId, providerId)
        return ResponseEntity.ok(mapOf("starBalance" to balance))
    }

    @GetMapping("/programs")
    fun getProgram(
        @RequestParam(required = false) providerId: UUID?
    ): ResponseEntity<LoyaltyProgram> {
        return ResponseEntity.ok(loyaltyService.getProgramForProvider(providerId))
    }

    @PostMapping("/programs")
    fun updateProgram(
        @RequestBody program: LoyaltyProgram,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyProgram> {
        if (role != "ADMIN" && role != "MERCHANT") {
            throw PaymentAccessDeniedException("Access denied: modifying loyalty programs requires ADMIN or MERCHANT role")
        }
        val actorId = if (!xUserId.isNullOrBlank()) UUID.fromString(xUserId) else UUID.randomUUID()
        val updated = loyaltyService.updateProgram(program, actorId)
        return ResponseEntity.status(HttpStatus.OK).body(updated)
    }

    @GetMapping("/audit-logs")
    fun getAuditLogs(
        @RequestParam(required = false) providerId: UUID?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<com.pawsnearme.paymentservice.model.LoyaltyAuditLog>> {
        if (role != "ADMIN" && role != "MERCHANT") {
            throw PaymentAccessDeniedException("Access denied: viewing audit logs requires ADMIN or MERCHANT role")
        }
        return ResponseEntity.ok(loyaltyService.getAuditLogs(providerId))
    }

    @ExceptionHandler(PaymentAccessDeniedException::class)
    fun handleAccessDenied(ex: PaymentAccessDeniedException): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
    }
}
