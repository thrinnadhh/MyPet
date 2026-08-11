package com.pawsnearme.paymentservice.controller

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.model.LoyaltyProgram
import com.pawsnearme.paymentservice.model.LoyaltyRewardInstance
import com.pawsnearme.paymentservice.service.LoyaltyLifecycleService
import com.pawsnearme.paymentservice.service.LoyaltyProgressResponse
import com.pawsnearme.paymentservice.service.LoyaltyReconciliationService
import com.pawsnearme.paymentservice.service.LoyaltyService
import com.pawsnearme.paymentservice.service.LoyaltyWalletRewardDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

data class ServiceCompletedEventPayload(
    val referenceId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val netAmount: BigDecimal,
    val serviceType: String,
)

data class ReserveRewardRequest(
    val code: String,
    val providerId: UUID,
    val orderId: UUID? = null
)

@RestController
@RequestMapping("/api/v1/loyalty")
class LoyaltyController(
    private val loyaltyService: LoyaltyService,
    private val providerModule: ProviderModuleApi,
    private val loyaltyLifecycleService: LoyaltyLifecycleService,
    private val loyaltyReconciliationService: LoyaltyReconciliationService,
    @Value("\${internal.api.secret:}") private val internalApiSecret: String,
) {
    @PostMapping("/welcome-star/claim")
    fun claimWelcomeStar(
        @RequestParam providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyProgressResponse> =
        ResponseEntity.ok(loyaltyService.claimWelcomeStar(requireUser(xUserId), providerId))

    @GetMapping("/progress")
    fun getProgress(
        @RequestParam providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyProgressResponse> =
        ResponseEntity.ok(loyaltyService.getProgress(requireUser(xUserId), providerId))

    @GetMapping("/wallet")
    fun getWallet(@RequestHeader("X-User-Id", required = false) xUserId: String?): ResponseEntity<List<LoyaltyWalletRewardDto>> =
        ResponseEntity.ok(loyaltyService.getCustomerWallet(requireUser(xUserId)))

    @GetMapping("/ledger")
    fun getLedger(
        @RequestParam(required = false) providerId: UUID?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<List<LoyaltyLedgerEntry>> =
        ResponseEntity.ok(loyaltyService.getLedgerHistory(requireUser(xUserId), providerId))

    @PostMapping("/rewards/reserve")
    fun reserveReward(
        @RequestBody req: ReserveRewardRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyRewardInstance> =
        ResponseEntity.ok(loyaltyService.reserveReward(req.code, requireUser(xUserId), req.providerId, req.orderId))

    @PostMapping("/rewards/release")
    fun releaseReward(
        @RequestParam code: String,
        @RequestParam(required = false) orderId: UUID?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        loyaltyService.releaseRewardReservation(code, requireUser(xUserId), orderId)
        return ResponseEntity.ok(mapOf("status" to "released"))
    }

    @PostMapping("/rewards/redeem")
    fun redeemReward(
        @RequestParam code: String,
        @RequestParam orderId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyRewardInstance> =
        ResponseEntity.ok(loyaltyService.redeemReward(code, requireUser(xUserId), orderId))

    @PostMapping("/events/order-delivered")
    fun handleOrderDelivered(
        @RequestBody payload: OrderDeliveredEventPayload,
        @RequestHeader("X-Internal-Secret", required = false) internalSecret: String?,
    ): ResponseEntity<Any> {
        requireInternalCaller(internalSecret)
        return ResponseEntity.ok(
            mapOf("processed" to loyaltyLifecycleService.recordDelivered(payload.orderId, payload.customerId, payload.providerId, payload.netAmount))
        )
    }

    @PostMapping("/events/order-refunded")
    fun handleOrderRefunded(
        @RequestBody payload: OrderRefundedEventPayload,
        @RequestHeader("X-Internal-Secret", required = false) internalSecret: String?,
    ): ResponseEntity<Any> {
        requireInternalCaller(internalSecret)
        return ResponseEntity.ok(
            mapOf("processed" to loyaltyLifecycleService.recordRefunded(payload.orderId, payload.customerId, payload.providerId))
        )
    }

    @PostMapping("/events/service-completed")
    fun handleServiceCompleted(
        @RequestBody payload: ServiceCompletedEventPayload,
        @RequestHeader("X-Internal-Secret", required = false) internalSecret: String?,
    ): ResponseEntity<Any> {
        requireInternalCaller(internalSecret)
        return ResponseEntity.ok(
            mapOf(
                "processed" to loyaltyLifecycleService.recordServiceCompleted(
                    payload.referenceId,
                    payload.customerId,
                    payload.providerId,
                    payload.netAmount,
                    payload.serviceType,
                )
            )
        )
    }

    @PostMapping("/reconcile")
    fun reconcileAccount(
        @RequestParam providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> = ResponseEntity.ok(
        mapOf("starBalance" to loyaltyReconciliationService.reconcile(requireUser(xUserId), providerId))
    )

    @GetMapping("/programs")
    fun getProgram(@RequestParam(required = false) providerId: UUID?): ResponseEntity<LoyaltyProgram> =
        ResponseEntity.ok(loyaltyService.getProgramForProvider(providerId))

    @PostMapping("/programs")
    fun updateProgram(
        @RequestBody program: LoyaltyProgram,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<LoyaltyProgram> {
        val actorId = requireUser(xUserId)
        authorizeProgramWrite(program.providerId, actorId, role)
        validateProgram(program)
        return ResponseEntity.ok(loyaltyService.updateProgram(program, actorId))
    }

    @GetMapping("/audit-logs")
    fun getAuditLogs(
        @RequestParam(required = false) providerId: UUID?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<List<com.pawsnearme.paymentservice.model.LoyaltyAuditLog>> {
        val actorId = requireUser(xUserId)
        authorizeProgramWrite(providerId, actorId, role)
        return ResponseEntity.ok(loyaltyService.getAuditLogs(providerId))
    }

    private fun authorizeProgramWrite(providerId: UUID?, actorId: UUID, role: String?) {
        if (role.equals("ADMIN", ignoreCase = true)) return
        if (!role.equals("MERCHANT", ignoreCase = true)) {
            throw PaymentAccessDeniedException("Modifying loyalty programs requires ADMIN or MERCHANT role")
        }
        val requestedProviderId = providerId
            ?: throw PaymentAccessDeniedException("Merchants may not modify the platform-default loyalty program")
        if (providerModule.ownerUserId(requestedProviderId) != actorId) {
            throw PaymentAccessDeniedException("Provider loyalty program is owned by another merchant")
        }
    }

    private fun validateProgram(program: LoyaltyProgram) {
        if (program.targetStars != 10) throw IllegalArgumentException("Loyalty target must remain 10 stars")
        if (program.rewardAmount !in ALLOWED_REWARDS) {
            throw IllegalArgumentException("Reward amount must be ₹50, ₹100, ₹150 or ₹200")
        }
        if (program.minOrderValue < BigDecimal.ZERO) throw IllegalArgumentException("Minimum order value cannot be negative")
        if (program.expiryDays !in 1..365) throw IllegalArgumentException("Reward expiry must be between 1 and 365 days")
        program.isStackable = true
    }

    private fun requireInternalCaller(providedSecret: String?) {
        if (internalApiSecret.isBlank() || providedSecret.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Internal loyalty event authorization is required")
        }
        val expected = internalApiSecret.toByteArray(StandardCharsets.UTF_8)
        val provided = providedSecret.toByteArray(StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(expected, provided)) {
            throw PaymentAccessDeniedException("Internal loyalty event authorization is invalid")
        }
    }

    private fun requireUser(value: String?): UUID = try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw PaymentAccessDeniedException("Valid authenticated user context is required")
    }

    @ExceptionHandler(PaymentAccessDeniedException::class)
    fun handleAccessDenied(ex: PaymentAccessDeniedException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))

    companion object {
        private val ALLOWED_REWARDS = setOf(
            BigDecimal("50"), BigDecimal("100"), BigDecimal("150"), BigDecimal("200")
        )
    }
}
