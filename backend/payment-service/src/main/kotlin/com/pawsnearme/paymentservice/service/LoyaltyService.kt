package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class LoyaltyProgressResponse(
    val providerId: UUID,
    val starBalance: Int,
    val targetStars: Int,
    val cycleCount: Int,
    val totalStarsEarned: Int,
    val welcomeStarClaimed: Boolean,
    val rewardAmount: BigDecimal,
    val isProgramActive: Boolean,
    val minOrderValue: BigDecimal
)

data class LoyaltyWalletRewardDto(
    val rewardId: UUID,
    val providerId: UUID,
    val rewardAmount: BigDecimal,
    val code: String,
    val status: RewardStatus,
    val expiresAt: Instant
)

@Service
class LoyaltyService(
    private val loyaltyProgramRepository: LoyaltyProgramRepository,
    private val customerLoyaltyAccountRepository: CustomerLoyaltyAccountRepository,
    private val loyaltyLedgerEntryRepository: LoyaltyLedgerEntryRepository,
    private val loyaltyRewardInstanceRepository: LoyaltyRewardInstanceRepository,
    private val loyaltyProcessedEventRepository: LoyaltyProcessedEventRepository,
    private val loyaltyAuditLogRepository: LoyaltyAuditLogRepository,
    private val outboxService: OutboxService
) {

    private val logger = LoggerFactory.getLogger(LoyaltyService::class.java)

    fun getProgramForProvider(providerId: UUID?): LoyaltyProgram {
        if (providerId != null) {
            val opt = loyaltyProgramRepository.findByProviderId(providerId)
            if (opt.isPresent) return opt.get()
        }
        return loyaltyProgramRepository.findByProviderIdIsNull().orElseGet {
            loyaltyProgramRepository.save(
                LoyaltyProgram(
                    providerId = null,
                    targetStars = 10,
                    rewardAmount = BigDecimal("50.00"),
                    minOrderValue = BigDecimal("199.00"),
                    welcomeStarPolicy = true,
                    isActive = true,
                    isStackable = false
                )
            )
        }
    }

    @Transactional
    fun getOrCreateAccount(customerId: UUID, providerId: UUID): CustomerLoyaltyAccount {
        return customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId)
            .orElseGet {
                customerLoyaltyAccountRepository.save(
                    CustomerLoyaltyAccount(
                        customerId = customerId,
                        providerId = providerId
                    )
                )
            }
    }

    @Transactional
    fun claimWelcomeStar(customerId: UUID, providerId: UUID): LoyaltyProgressResponse {
        val program = getProgramForProvider(providerId)
        if (!program.isActive) {
            throw IllegalArgumentException("Loyalty program is not active for this provider")
        }
        if (!program.welcomeStarPolicy) {
            throw IllegalArgumentException("Welcome stars are disabled for this program")
        }

        val account = getOrCreateAccount(customerId, providerId)
        if (account.welcomeStarClaimed) {
            return getProgress(customerId, providerId)
        }

        account.welcomeStarClaimed = true
        account.starBalance += 1
        account.totalStarsEarned += 1
        account.updatedAt = Instant.now()
        customerLoyaltyAccountRepository.save(account)

        loyaltyLedgerEntryRepository.save(
            LoyaltyLedgerEntry(
                customerId = customerId,
                providerId = providerId,
                deltaStars = 1,
                entryType = LedgerEntryType.WELCOME_STAR,
                referenceId = providerId,
                actorId = customerId,
                note = "Welcome star claimed"
            )
        )

        checkAndApplyRollover(account, program)

        publishLoyaltyEvent("WelcomeStarClaimed", customerId, providerId, mapOf("starBalance" to account.starBalance, "welcomeStarClaimed" to true))

        return getProgress(customerId, providerId)
    }

    @Transactional
    fun processOrderDeliveredEvent(orderId: UUID, customerId: UUID, providerId: UUID, netAmount: BigDecimal): Boolean {
        if (loyaltyProcessedEventRepository.existsByEventTypeAndReferenceId("ORDER_DELIVERED", orderId)) {
            logger.info("Order delivered event already processed for orderId {}", orderId)
            return false
        }

        loyaltyProcessedEventRepository.save(
            LoyaltyProcessedEvent(
                eventType = "ORDER_DELIVERED",
                referenceId = orderId
            )
        )

        val program = getProgramForProvider(providerId)
        if (!program.isActive) {
            logger.info("Loyalty program not active for provider {}", providerId)
            return false
        }

        if (netAmount < program.minOrderValue) {
            logger.info("Order amount {} below minimum threshold {} for provider {}", netAmount, program.minOrderValue, providerId)
            return false
        }

        val account = getOrCreateAccount(customerId, providerId)
        account.starBalance += 1
        account.totalStarsEarned += 1
        account.updatedAt = Instant.now()
        customerLoyaltyAccountRepository.save(account)

        loyaltyLedgerEntryRepository.save(
            LoyaltyLedgerEntry(
                customerId = customerId,
                providerId = providerId,
                deltaStars = 1,
                entryType = LedgerEntryType.PURCHASE_STAR,
                referenceId = orderId,
                actorId = customerId,
                note = "Purchase star earned for order $orderId"
            )
        )

        checkAndApplyRollover(account, program)

        publishLoyaltyEvent("PurchaseStarEarned", customerId, providerId, mapOf("orderId" to orderId, "starBalance" to account.starBalance))
        return true
    }

    private fun checkAndApplyRollover(account: CustomerLoyaltyAccount, program: LoyaltyProgram) {
        if (account.starBalance >= program.targetStars) {
            account.starBalance -= program.targetStars
            account.cycleCount += 1
            account.totalRewardsIssued += 1
            account.updatedAt = Instant.now()
            customerLoyaltyAccountRepository.save(account)

            val code = "RWD-${UUID.randomUUID().toString().take(8).uppercase()}"
            val reward = loyaltyRewardInstanceRepository.save(
                LoyaltyRewardInstance(
                    customerId = account.customerId,
                    providerId = account.providerId,
                    rewardAmount = program.rewardAmount,
                    status = RewardStatus.ISSUED,
                    code = code,
                    expiresAt = Instant.now().plusSeconds(program.expiryDays * 86400L)
                )
            )

            loyaltyLedgerEntryRepository.save(
                LoyaltyLedgerEntry(
                    customerId = account.customerId,
                    providerId = account.providerId,
                    deltaStars = -program.targetStars,
                    entryType = LedgerEntryType.CYCLE_ROLLOVER,
                    referenceId = reward.rewardId,
                    actorId = account.customerId,
                    note = "Cycle completed: issued reward $code for ₹${program.rewardAmount}"
                )
            )

            publishLoyaltyEvent("RewardIssued", account.customerId, account.providerId, mapOf("rewardId" to reward.rewardId, "code" to code, "amount" to program.rewardAmount))
        }
    }

    fun getProgress(customerId: UUID, providerId: UUID): LoyaltyProgressResponse {
        val program = getProgramForProvider(providerId)
        val account = customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId).orElse(null)

        return LoyaltyProgressResponse(
            providerId = providerId,
            starBalance = account?.starBalance ?: 0,
            targetStars = program.targetStars,
            cycleCount = account?.cycleCount ?: 0,
            totalStarsEarned = account?.totalStarsEarned ?: 0,
            welcomeStarClaimed = account?.welcomeStarClaimed ?: false,
            rewardAmount = program.rewardAmount,
            isProgramActive = program.isActive,
            minOrderValue = program.minOrderValue
        )
    }

    fun getCustomerWallet(customerId: UUID): List<LoyaltyWalletRewardDto> {
        val rewards = loyaltyRewardInstanceRepository.findByCustomerIdAndStatusIn(
            customerId,
            listOf(RewardStatus.ISSUED, RewardStatus.RESERVED)
        )
        return rewards.map {
            LoyaltyWalletRewardDto(
                rewardId = it.rewardId!!,
                providerId = it.providerId,
                rewardAmount = it.rewardAmount,
                code = it.code,
                status = it.status,
                expiresAt = it.expiresAt
            )
        }
    }

    fun getLedgerHistory(customerId: UUID, providerId: UUID?): List<LoyaltyLedgerEntry> {
        return if (providerId != null) {
            loyaltyLedgerEntryRepository.findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId, providerId)
        } else {
            loyaltyLedgerEntryRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        }
    }

    fun validateReward(code: String, customerId: UUID, providerId: UUID): LoyaltyRewardInstance {
        val reward = loyaltyRewardInstanceRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Invalid reward code") }

        if (reward.customerId != customerId) {
            throw IllegalArgumentException("Reward code belongs to a different customer")
        }
        if (reward.providerId != providerId) {
            throw IllegalArgumentException("Reward code is not applicable for this provider")
        }
        if (reward.status != RewardStatus.ISSUED && reward.status != RewardStatus.RESERVED) {
            throw IllegalArgumentException("Reward is not active (${reward.status})")
        }
        if (Instant.now().isAfter(reward.expiresAt)) {
            reward.status = RewardStatus.EXPIRED
            loyaltyRewardInstanceRepository.save(reward)
            throw IllegalArgumentException("Reward has expired")
        }

        return reward
    }

    @Transactional
    fun reserveReward(code: String, customerId: UUID, providerId: UUID, orderId: UUID?): LoyaltyRewardInstance {
        val reward = validateReward(code, customerId, providerId)
        reward.status = RewardStatus.RESERVED
        reward.orderId = orderId
        val saved = loyaltyRewardInstanceRepository.save(reward)
        publishLoyaltyEvent("RewardReserved", customerId, providerId, mapOf("rewardId" to saved.rewardId, "orderId" to orderId))
        return saved
    }

    @Transactional
    fun releaseRewardReservation(code: String, customerId: UUID, orderId: UUID?) {
        val rewardOpt = loyaltyRewardInstanceRepository.findByCode(code)
        if (rewardOpt.isPresent) {
            val reward = rewardOpt.get()
            if (reward.customerId == customerId && reward.status == RewardStatus.RESERVED) {
                reward.status = RewardStatus.ISSUED
                reward.orderId = null
                loyaltyRewardInstanceRepository.save(reward)
                publishLoyaltyEvent("RewardReleased", customerId, reward.providerId, mapOf("rewardId" to reward.rewardId, "orderId" to orderId))
            }
        }
    }

    @Transactional
    fun redeemReward(code: String, customerId: UUID, orderId: UUID): LoyaltyRewardInstance {
        val reward = loyaltyRewardInstanceRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Invalid reward code") }

        if (reward.customerId != customerId) {
            throw IllegalArgumentException("Reward code belongs to a different customer")
        }

        reward.status = RewardStatus.REDEEMED
        reward.orderId = orderId
        reward.usedAt = Instant.now()
        val saved = loyaltyRewardInstanceRepository.save(reward)
        publishLoyaltyEvent("RewardRedeemed", customerId, reward.providerId, mapOf("rewardId" to saved.rewardId, "orderId" to orderId))
        return saved
    }

    @Transactional
    fun processOrderRefundEvent(orderId: UUID, customerId: UUID, providerId: UUID): Boolean {
        if (loyaltyProcessedEventRepository.existsByEventTypeAndReferenceId("ORDER_REFUNDED", orderId)) {
            return false
        }

        loyaltyProcessedEventRepository.save(
            LoyaltyProcessedEvent(
                eventType = "ORDER_REFUNDED",
                referenceId = orderId
            )
        )

        val account = getOrCreateAccount(customerId, providerId)

        loyaltyLedgerEntryRepository.save(
            LoyaltyLedgerEntry(
                customerId = customerId,
                providerId = providerId,
                deltaStars = -1,
                entryType = LedgerEntryType.STAR_REVERSAL,
                referenceId = orderId,
                actorId = customerId,
                note = "Reversed 1 star due to order refund for $orderId"
            )
        )

        if (account.starBalance > 0) {
            account.starBalance -= 1
        }
        if (account.totalStarsEarned > 0) {
            account.totalStarsEarned -= 1
        }
        account.updatedAt = Instant.now()
        customerLoyaltyAccountRepository.save(account)

        val activeRewards = loyaltyRewardInstanceRepository.findByCustomerIdAndProviderIdAndStatusIn(
            customerId,
            providerId,
            listOf(RewardStatus.ISSUED)
        )
        if (activeRewards.isNotEmpty()) {
            val latestReward = activeRewards.last()
            latestReward.status = RewardStatus.REVOKED
            loyaltyRewardInstanceRepository.save(latestReward)
            publishLoyaltyEvent("RewardRevoked", customerId, providerId, mapOf("rewardId" to latestReward.rewardId, "orderId" to orderId))
        }

        publishLoyaltyEvent("StarReversed", customerId, providerId, mapOf("orderId" to orderId, "starBalance" to account.starBalance))
        return true
    }

    @Transactional
    fun reconcileAccountFromLedger(customerId: UUID, providerId: UUID): Int {
        val entries = loyaltyLedgerEntryRepository.findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId, providerId)
        val calculatedSum = entries.filter { it.entryType != LedgerEntryType.CYCLE_ROLLOVER }.sumOf { it.deltaStars }

        val account = getOrCreateAccount(customerId, providerId)
        if (account.starBalance != calculatedSum) {
            logger.warn("Reconciling account for customer {}: starBalance was {}, calculated was {}", customerId, account.starBalance, calculatedSum)
            account.starBalance = calculatedSum.coerceAtLeast(0)
            account.updatedAt = Instant.now()
            customerLoyaltyAccountRepository.save(account)
        }
        return account.starBalance
    }

    @Transactional
    fun updateProgram(program: LoyaltyProgram, actorId: UUID = UUID.randomUUID()): LoyaltyProgram {
        val existingOpt = if (program.providerId != null) {
            loyaltyProgramRepository.findByProviderId(program.providerId!!)
        } else {
            loyaltyProgramRepository.findByProviderIdIsNull()
        }

        val beforeJson = existingOpt.map { "active=${it.isActive}, rewardAmount=${it.rewardAmount}, minOrderValue=${it.minOrderValue}" }.orElse("NEW")

        val toSave = if (existingOpt.isPresent) {
            val existing = existingOpt.get()
            existing.targetStars = program.targetStars
            existing.rewardAmount = program.rewardAmount
            existing.minOrderValue = program.minOrderValue
            existing.welcomeStarPolicy = program.welcomeStarPolicy
            existing.isActive = program.isActive
            existing.isStackable = program.isStackable
            existing.expiryDays = program.expiryDays
            existing.updatedAt = Instant.now()
            existing
        } else {
            program
        }

        val saved = loyaltyProgramRepository.save(toSave)
        val afterJson = "active=${saved.isActive}, rewardAmount=${saved.rewardAmount}, minOrderValue=${saved.minOrderValue}"

        loyaltyAuditLogRepository.save(
            LoyaltyAuditLog(
                actorId = actorId,
                providerId = saved.providerId,
                action = "UPDATE_PROGRAM",
                beforeJson = beforeJson,
                afterJson = afterJson
            )
        )

        return saved
    }

    fun getAuditLogs(providerId: UUID?): List<LoyaltyAuditLog> {
        return if (providerId != null) {
            loyaltyAuditLogRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
        } else {
            loyaltyAuditLogRepository.findAllByOrderByCreatedAtDesc()
        }
    }

    private fun publishLoyaltyEvent(eventType: String, customerId: UUID, providerId: UUID, payload: Map<String, Any?>) {
        val eventPayload = mapOf(
            "eventType" to eventType,
            "customerId" to customerId.toString(),
            "providerId" to providerId.toString(),
            "occurredAt" to Instant.now().toString(),
            "data" to payload
        )
        outboxService.saveEvent(
            eventId = UUID.randomUUID(),
            aggregateType = "LOYALTY",
            aggregateId = customerId,
            eventType = eventType,
            eventPayload = eventPayload
        )
    }
}
