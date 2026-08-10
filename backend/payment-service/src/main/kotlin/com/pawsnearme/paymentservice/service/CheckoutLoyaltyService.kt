package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.module.LoyaltyRewardTerms
import com.pawsnearme.paymentservice.model.RewardStatus
import com.pawsnearme.paymentservice.repository.LoyaltyRewardInstanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CheckoutLoyaltyService(
    private val rewardRepository: LoyaltyRewardInstanceRepository,
    private val loyaltyService: LoyaltyService,
) {
    fun terms(rewardId: UUID, customerId: UUID, providerId: UUID): LoyaltyRewardTerms {
        val reward = rewardRepository.findById(rewardId)
            .orElseThrow { IllegalArgumentException("Loyalty reward not found") }
        val validated = loyaltyService.validateReward(reward.code, customerId, providerId)
        val program = loyaltyService.getProgramForProvider(providerId)
        return LoyaltyRewardTerms(
            rewardId = requireNotNull(validated.rewardId),
            code = validated.code,
            amount = validated.rewardAmount,
            stackableWithCoupon = program.isStackable,
        )
    }

    @Transactional
    fun reserve(rewardId: UUID, customerId: UUID, providerId: UUID, orderId: UUID) {
        val reward = rewardRepository.findById(rewardId)
            .orElseThrow { IllegalArgumentException("Loyalty reward not found") }
        loyaltyService.reserveReward(reward.code, customerId, providerId, orderId)
    }

    @Transactional
    fun release(rewardId: UUID, customerId: UUID, orderId: UUID) {
        val reward = rewardRepository.findById(rewardId).orElse(null) ?: return
        if (reward.customerId != customerId || reward.orderId != orderId) return
        if (reward.status == RewardStatus.RESERVED) {
            loyaltyService.releaseRewardReservation(reward.code, customerId, orderId)
        }
    }

    @Transactional
    fun redeem(rewardId: UUID, customerId: UUID, orderId: UUID) {
        val reward = rewardRepository.findById(rewardId)
            .orElseThrow { IllegalArgumentException("Loyalty reward not found") }
        if (Instant.now().isAfter(reward.expiresAt)) {
            throw IllegalArgumentException("Loyalty reward has expired")
        }
        loyaltyService.redeemReward(reward.code, customerId, orderId)
    }
}
