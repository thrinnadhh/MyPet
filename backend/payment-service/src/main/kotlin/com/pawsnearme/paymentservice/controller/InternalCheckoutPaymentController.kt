package com.pawsnearme.paymentservice.controller

import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PrepareOrderPaymentCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/payments")
class InternalCheckoutPaymentController(
    private val paymentModule: PaymentModuleApi,
    @Value("\${internal.api.secret:}") private val internalApiSecret: String,
) {
    @PostMapping("/orders/prepare")
    fun prepareOrderPayment(
        @RequestBody command: PrepareOrderPaymentCommand,
        @RequestHeader("X-Internal-Secret", required = false) secret: String?,
    ) = authorized(secret) { ResponseEntity.ok(paymentModule.prepareOrderPayment(command)) }

    @PostMapping("/orders/{orderId}/expire")
    fun expireOrderPayment(
        @PathVariable orderId: UUID,
        @RequestParam reason: String,
        @RequestHeader("X-Internal-Secret", required = false) secret: String?,
    ) = authorized(secret) {
        ResponseEntity.ok(paymentModule.expireOrderPayment(orderId, reason))
    }

    @GetMapping("/loyalty/{rewardId}")
    fun loyaltyTerms(
        @PathVariable rewardId: UUID,
        @RequestParam customerId: UUID,
        @RequestParam providerId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) secret: String?,
    ) = authorized(secret) {
        ResponseEntity.ok(paymentModule.loyaltyRewardTerms(rewardId, customerId, providerId))
    }

    @PostMapping("/loyalty/{rewardId}/reserve")
    fun reserveLoyalty(
        @PathVariable rewardId: UUID,
        @RequestParam customerId: UUID,
        @RequestParam providerId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) secret: String?,
    ) = authorized(secret) {
        paymentModule.reserveLoyaltyReward(rewardId, customerId, providerId, orderId)
        ResponseEntity.ok(mapOf("status" to "reserved"))
    }

    @PostMapping("/loyalty/{rewardId}/release")
    fun releaseLoyalty(
        @PathVariable rewardId: UUID,
        @RequestParam customerId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) secret: String?,
    ) = authorized(secret) {
        paymentModule.releaseLoyaltyReward(rewardId, customerId, orderId)
        ResponseEntity.ok(mapOf("status" to "released"))
    }

    @PostMapping("/loyalty/{rewardId}/redeem")
    fun redeemLoyalty(
        @PathVariable rewardId: UUID,
        @RequestParam customerId: UUID,
        @RequestParam orderId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) secret: String?,
    ) = authorized(secret) {
        paymentModule.redeemLoyaltyReward(rewardId, customerId, orderId)
        ResponseEntity.ok(mapOf("status" to "redeemed"))
    }

    private fun <T> authorized(provided: String?, block: () -> ResponseEntity<T>): ResponseEntity<T> {
        if (internalApiSecret.isBlank() || provided.isNullOrBlank()) {
            throw PaymentAccessDeniedException("Internal payment authorization is required")
        }
        if (!MessageDigest.isEqual(
                internalApiSecret.toByteArray(StandardCharsets.UTF_8),
                provided.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            throw PaymentAccessDeniedException("Internal payment authorization is invalid")
        }
        return block()
    }
}
