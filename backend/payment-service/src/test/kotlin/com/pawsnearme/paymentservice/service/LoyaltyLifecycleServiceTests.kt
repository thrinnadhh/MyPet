package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.model.LedgerEntryType
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.model.LoyaltyProgram
import com.pawsnearme.paymentservice.model.LoyaltyRewardInstance
import com.pawsnearme.paymentservice.model.LoyaltyStarDebt
import com.pawsnearme.paymentservice.model.RewardStatus
import com.pawsnearme.paymentservice.repository.CustomerLoyaltyAccountRepository
import com.pawsnearme.paymentservice.repository.LoyaltyLedgerEntryRepository
import com.pawsnearme.paymentservice.repository.LoyaltyProcessedEventRepository
import com.pawsnearme.paymentservice.repository.LoyaltyRewardInstanceRepository
import com.pawsnearme.paymentservice.repository.LoyaltyStarDebtRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class LoyaltyLifecycleServiceTests {
    private val loyaltyService: LoyaltyService = mock()
    private val accountRepository: CustomerLoyaltyAccountRepository = mock()
    private val ledgerRepository: LoyaltyLedgerEntryRepository = mock()
    private val rewardRepository: LoyaltyRewardInstanceRepository = mock()
    private val processedRepository: LoyaltyProcessedEventRepository = mock()
    private val debtRepository: LoyaltyStarDebtRepository = mock()
    private val outboxService: OutboxService = mock()
    private val service = LoyaltyLifecycleService(
        loyaltyService,
        accountRepository,
        ledgerRepository,
        rewardRepository,
        processedRepository,
        debtRepository,
        outboxService,
    )

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val program = LoyaltyProgram(
        providerId = providerId,
        targetStars = 10,
        rewardAmount = BigDecimal("50.00"),
        minOrderValue = BigDecimal("199.00"),
        isActive = true,
    )

    @BeforeEach
    fun setup() {
        whenever(loyaltyService.getProgramForProvider(providerId)).thenReturn(program)
        whenever(processedRepository.existsByEventTypeAndReferenceId(any(), any())).thenReturn(false)
        whenever(accountRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(ledgerRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(debtRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(debtRepository.findByCustomerIdAndProviderId(customerId, providerId)).thenReturn(Optional.empty())
        whenever(rewardRepository.findByCustomerIdAndProviderIdAndStatusIn(any(), any(), any())).thenReturn(emptyList())
    }

    @Test
    fun `refunding an order that never earned a purchase star does not remove another star`() {
        val orderId = UUID.randomUUID()
        whenever(ledgerRepository.findByReferenceId(orderId)).thenReturn(emptyList())

        val processed = service.recordRefunded(orderId, customerId, providerId)

        assertFalse(processed)
        verify(accountRepository, never()).save(any())
        verify(rewardRepository, never()).save(any())
        verify(debtRepository, never()).save(any())
    }

    @Test
    fun `refund subtracts the matching purchase star from current balance`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 3,
            totalStarsEarned = 3,
        )
        whenever(loyaltyService.getOrCreateAccount(customerId, providerId)).thenReturn(account)
        whenever(ledgerRepository.findByReferenceId(orderId)).thenReturn(listOf(purchaseStar(orderId)))

        val processed = service.recordRefunded(orderId, customerId, providerId)

        assertTrue(processed)
        assertEquals(2, account.starBalance)
        assertEquals(2, account.totalStarsEarned)
        verify(ledgerRepository).save(org.mockito.kotlin.check {
            assertEquals(LedgerEntryType.STAR_REVERSAL, it.entryType)
            assertEquals(orderId, it.referenceId)
        })
    }

    @Test
    fun `refund revokes unspent reward and restores nine valid stars when balance is zero`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 0,
            cycleCount = 1,
            totalStarsEarned = 10,
            totalRewardsIssued = 1,
        )
        val reward = LoyaltyRewardInstance(
            rewardId = UUID.randomUUID(),
            customerId = customerId,
            providerId = providerId,
            rewardAmount = BigDecimal("50.00"),
            status = RewardStatus.ISSUED,
            code = "RWD-TEST",
        )
        whenever(loyaltyService.getOrCreateAccount(customerId, providerId)).thenReturn(account)
        whenever(ledgerRepository.findByReferenceId(orderId)).thenReturn(listOf(purchaseStar(orderId)))
        whenever(rewardRepository.findByCustomerIdAndProviderIdAndStatusIn(
            customerId,
            providerId,
            listOf(RewardStatus.ISSUED, RewardStatus.RESERVED),
        )).thenReturn(listOf(reward))
        whenever(rewardRepository.save(any())).thenAnswer { it.getArgument(0) }

        val processed = service.recordRefunded(orderId, customerId, providerId)

        assertTrue(processed)
        assertEquals(RewardStatus.REVOKED, reward.status)
        assertEquals(9, account.starBalance)
        assertEquals(0, account.cycleCount)
        assertEquals(0, account.totalRewardsIssued)
    }

    @Test
    fun `refund after reward consumption creates one star debt instead of an unrelated reversal`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 0,
            cycleCount = 1,
            totalStarsEarned = 10,
            totalRewardsIssued = 1,
        )
        whenever(loyaltyService.getOrCreateAccount(customerId, providerId)).thenReturn(account)
        whenever(ledgerRepository.findByReferenceId(orderId)).thenReturn(listOf(purchaseStar(orderId)))

        val processed = service.recordRefunded(orderId, customerId, providerId)

        assertTrue(processed)
        verify(debtRepository).save(org.mockito.kotlin.check<LoyaltyStarDebt> {
            assertEquals(1, it.debtStars)
            assertEquals(customerId, it.customerId)
            assertEquals(providerId, it.providerId)
        })
        assertEquals(0, account.starBalance)
    }

    @Test
    fun `next qualifying purchase pays outstanding refund debt before increasing visible stars`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 0,
            totalStarsEarned = 10,
        )
        val debt = LoyaltyStarDebt(customerId = customerId, providerId = providerId, debtStars = 1)
        whenever(loyaltyService.getOrCreateAccount(customerId, providerId)).thenReturn(account)
        whenever(debtRepository.findByCustomerIdAndProviderId(customerId, providerId)).thenReturn(Optional.of(debt))

        val processed = service.recordDelivered(orderId, customerId, providerId, BigDecimal("500.00"))

        assertTrue(processed)
        assertEquals(0, debt.debtStars)
        assertEquals(0, account.starBalance)
        assertEquals(11, account.totalStarsEarned)
    }

    private fun purchaseStar(orderId: UUID) = LoyaltyLedgerEntry(
        customerId = customerId,
        providerId = providerId,
        deltaStars = 1,
        entryType = LedgerEntryType.PURCHASE_STAR,
        referenceId = orderId,
        actorId = customerId,
    )
}
