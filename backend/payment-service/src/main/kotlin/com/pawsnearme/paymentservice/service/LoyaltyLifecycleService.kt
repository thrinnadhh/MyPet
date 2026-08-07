package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.model.LedgerEntryType
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.model.LoyaltyProcessedEvent
import com.pawsnearme.paymentservice.model.LoyaltyRewardInstance
import com.pawsnearme.paymentservice.model.LoyaltyStarDebt
import com.pawsnearme.paymentservice.model.RewardStatus
import com.pawsnearme.paymentservice.repository.CustomerLoyaltyAccountRepository
import com.pawsnearme.paymentservice.repository.LoyaltyLedgerEntryRepository
import com.pawsnearme.paymentservice.repository.LoyaltyProcessedEventRepository
import com.pawsnearme.paymentservice.repository.LoyaltyRewardInstanceRepository
import com.pawsnearme.paymentservice.repository.LoyaltyStarDebtRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Handles order-driven loyalty state changes. This service is deliberately
 * separate from customer wallet operations because delivered/refunded events
 * are internal lifecycle events and must preserve idempotency, rollover and
 * post-refund debt invariants.
 */
@Service
class LoyaltyLifecycleService(
    private val loyaltyService: LoyaltyService,
    private val accountRepository: CustomerLoyaltyAccountRepository,
    private val ledgerRepository: LoyaltyLedgerEntryRepository,
    private val rewardRepository: LoyaltyRewardInstanceRepository,
    private val processedEventRepository: LoyaltyProcessedEventRepository,
    private val debtRepository: LoyaltyStarDebtRepository,
    private val outboxService: OutboxService,
) {
    @Transactional
    fun recordDelivered(orderId: UUID, customerId: UUID, providerId: UUID, netAmount: BigDecimal): Boolean {
        if (processedEventRepository.existsByEventTypeAndReferenceId(ORDER_DELIVERED, orderId)) return false
        processedEventRepository.save(LoyaltyProcessedEvent(eventType = ORDER_DELIVERED, referenceId = orderId))

        val program = loyaltyService.getProgramForProvider(providerId)
        if (!program.isActive || netAmount < program.minOrderValue) return false

        val account = loyaltyService.getOrCreateAccount(customerId, providerId)
        val purchaseEntry = ledgerRepository.save(
            LoyaltyLedgerEntry(
                customerId = customerId,
                providerId = providerId,
                deltaStars = 1,
                entryType = LedgerEntryType.PURCHASE_STAR,
                referenceId = orderId,
                actorId = customerId,
                note = "Purchase star earned for order $orderId",
            )
        )
        account.totalStarsEarned += 1

        val debt = debtRepository.findByCustomerIdAndProviderId(customerId, providerId).orElse(null)
        if (debt != null && debt.debtStars > 0) {
            debt.debtStars -= 1
            debt.updatedAt = Instant.now()
            debtRepository.save(debt)
            ledgerRepository.save(
                LoyaltyLedgerEntry(
                    customerId = customerId,
                    providerId = providerId,
                    deltaStars = -1,
                    entryType = LedgerEntryType.ADMIN_ADJUSTMENT,
                    referenceId = orderId,
                    actorId = customerId,
                    note = "Applied purchase star ${purchaseEntry.entryId ?: orderId} to outstanding refund debt",
                )
            )
            account.updatedAt = Instant.now()
            accountRepository.save(account)
            publish("PurchaseStarAppliedToDebt", customerId, providerId, mapOf("orderId" to orderId, "remainingDebt" to debt.debtStars))
            return true
        }

        account.starBalance += 1
        account.updatedAt = Instant.now()
        accountRepository.save(account)
        applyRolloverIfEligible(account, program.targetStars, program.rewardAmount, program.expiryDays)
        publish("PurchaseStarEarned", customerId, providerId, mapOf("orderId" to orderId, "starBalance" to account.starBalance))
        return true
    }

    @Transactional
    fun recordRefunded(orderId: UUID, customerId: UUID, providerId: UUID): Boolean {
        if (processedEventRepository.existsByEventTypeAndReferenceId(ORDER_REFUNDED, orderId)) return false
        processedEventRepository.save(LoyaltyProcessedEvent(eventType = ORDER_REFUNDED, referenceId = orderId))

        val purchaseStar = ledgerRepository.findByReferenceId(orderId).firstOrNull {
            it.customerId == customerId &&
                it.providerId == providerId &&
                it.entryType == LedgerEntryType.PURCHASE_STAR &&
                it.deltaStars > 0
        } ?: return false

        val alreadyReversed = ledgerRepository.findByReferenceId(orderId).any {
            it.customerId == customerId &&
                it.providerId == providerId &&
                it.entryType == LedgerEntryType.STAR_REVERSAL
        }
        if (alreadyReversed) return false

        val account = loyaltyService.getOrCreateAccount(customerId, providerId)
        val program = loyaltyService.getProgramForProvider(providerId)

        ledgerRepository.save(
            LoyaltyLedgerEntry(
                customerId = customerId,
                providerId = providerId,
                deltaStars = -purchaseStar.deltaStars,
                entryType = LedgerEntryType.STAR_REVERSAL,
                referenceId = orderId,
                actorId = customerId,
                note = "Reversed purchase star due to refund for order $orderId",
            )
        )
        account.totalStarsEarned = (account.totalStarsEarned - purchaseStar.deltaStars).coerceAtLeast(0)

        when {
            account.starBalance >= purchaseStar.deltaStars -> {
                account.starBalance -= purchaseStar.deltaStars
            }
            else -> {
                val activeReward = rewardRepository.findByCustomerIdAndProviderIdAndStatusIn(
                    customerId,
                    providerId,
                    listOf(RewardStatus.ISSUED, RewardStatus.RESERVED),
                ).maxByOrNull { it.issuedAt }

                if (activeReward != null) {
                    activeReward.status = RewardStatus.REVOKED
                    activeReward.orderId = null
                    rewardRepository.save(activeReward)
                    account.cycleCount = (account.cycleCount - 1).coerceAtLeast(0)
                    account.totalRewardsIssued = (account.totalRewardsIssued - 1).coerceAtLeast(0)
                    account.starBalance += (program.targetStars - purchaseStar.deltaStars).coerceAtLeast(0)
                    ledgerRepository.save(
                        LoyaltyLedgerEntry(
                            customerId = customerId,
                            providerId = providerId,
                            deltaStars = program.targetStars,
                            entryType = LedgerEntryType.ADMIN_ADJUSTMENT,
                            referenceId = activeReward.rewardId,
                            actorId = customerId,
                            note = "Restored rollover stars after revoking reward ${activeReward.code} for refunded order $orderId",
                        )
                    )
                    publish("RewardRevoked", customerId, providerId, mapOf("rewardId" to activeReward.rewardId, "orderId" to orderId))
                } else {
                    val debt = debtRepository.findByCustomerIdAndProviderId(customerId, providerId).orElseGet {
                        LoyaltyStarDebt(customerId = customerId, providerId = providerId)
                    }
                    debt.debtStars += purchaseStar.deltaStars
                    debt.updatedAt = Instant.now()
                    debtRepository.save(debt)
                    publish("StarDebtCreated", customerId, providerId, mapOf("orderId" to orderId, "debtStars" to debt.debtStars))
                }
            }
        }

        account.updatedAt = Instant.now()
        accountRepository.save(account)
        publish("StarReversed", customerId, providerId, mapOf("orderId" to orderId, "starBalance" to account.starBalance))
        return true
    }

    private fun applyRolloverIfEligible(
        account: CustomerLoyaltyAccount,
        targetStars: Int,
        rewardAmount: BigDecimal,
        expiryDays: Int,
    ) {
        if (account.starBalance < targetStars) return
        account.starBalance -= targetStars
        account.cycleCount += 1
        account.totalRewardsIssued += 1
        account.updatedAt = Instant.now()
        accountRepository.save(account)

        val code = "RWD-${UUID.randomUUID().toString().take(8).uppercase()}"
        val reward = rewardRepository.save(
            LoyaltyRewardInstance(
                customerId = account.customerId,
                providerId = account.providerId,
                rewardAmount = rewardAmount,
                status = RewardStatus.ISSUED,
                code = code,
                expiresAt = Instant.now().plusSeconds(expiryDays * 86400L),
            )
        )
        ledgerRepository.save(
            LoyaltyLedgerEntry(
                customerId = account.customerId,
                providerId = account.providerId,
                deltaStars = -targetStars,
                entryType = LedgerEntryType.CYCLE_ROLLOVER,
                referenceId = reward.rewardId,
                actorId = account.customerId,
                note = "Cycle completed: issued reward $code for ₹$rewardAmount",
            )
        )
        publish("RewardIssued", account.customerId, account.providerId, mapOf("rewardId" to reward.rewardId, "code" to code, "amount" to rewardAmount))
    }

    private fun publish(eventType: String, customerId: UUID, providerId: UUID, data: Map<String, Any?>) {
        outboxService.saveEvent(
            eventId = UUID.randomUUID(),
            aggregateType = "LOYALTY",
            aggregateId = customerId,
            eventType = eventType,
            eventPayload = mapOf(
                "eventType" to eventType,
                "customerId" to customerId.toString(),
                "providerId" to providerId.toString(),
                "occurredAt" to Instant.now().toString(),
                "data" to data,
            ),
        )
    }

    companion object {
        private const val ORDER_DELIVERED = "ORDER_DELIVERED"
        private const val ORDER_REFUNDED = "ORDER_REFUNDED"
    }
}
