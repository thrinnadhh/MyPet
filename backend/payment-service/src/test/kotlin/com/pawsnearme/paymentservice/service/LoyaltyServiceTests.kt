package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class LoyaltyServiceTests {

    private lateinit var loyaltyProgramRepository: LoyaltyProgramRepository
    private lateinit var customerLoyaltyAccountRepository: CustomerLoyaltyAccountRepository
    private lateinit var loyaltyLedgerEntryRepository: LoyaltyLedgerEntryRepository
    private lateinit var loyaltyRewardInstanceRepository: LoyaltyRewardInstanceRepository
    private lateinit var loyaltyProcessedEventRepository: LoyaltyProcessedEventRepository
    private lateinit var loyaltyAuditLogRepository: LoyaltyAuditLogRepository
    private lateinit var outboxService: OutboxService

    private lateinit var service: LoyaltyService

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        loyaltyProgramRepository = mock()
        customerLoyaltyAccountRepository = mock()
        loyaltyLedgerEntryRepository = mock()
        loyaltyRewardInstanceRepository = mock()
        loyaltyProcessedEventRepository = mock()
        loyaltyAuditLogRepository = mock()
        outboxService = mock()

        service = LoyaltyService(
            loyaltyProgramRepository = loyaltyProgramRepository,
            customerLoyaltyAccountRepository = customerLoyaltyAccountRepository,
            loyaltyLedgerEntryRepository = loyaltyLedgerEntryRepository,
            loyaltyRewardInstanceRepository = loyaltyRewardInstanceRepository,
            loyaltyProcessedEventRepository = loyaltyProcessedEventRepository,
            loyaltyAuditLogRepository = loyaltyAuditLogRepository,
            outboxService = outboxService
        )

        val defaultProgram = LoyaltyProgram(
            providerId = providerId,
            targetStars = 10,
            rewardAmount = BigDecimal("50.00"),
            minOrderValue = BigDecimal("199.00"),
            welcomeStarPolicy = true,
            isActive = true
        )
        whenever(loyaltyProgramRepository.findByProviderId(any())).thenReturn(Optional.of(defaultProgram))
        whenever(loyaltyProgramRepository.findByProviderIdIsNull()).thenReturn(Optional.of(defaultProgram))
        whenever(customerLoyaltyAccountRepository.ensureAccount(any(), any())).thenReturn(1)
        whenever(customerLoyaltyAccountRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(loyaltyRewardInstanceRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(loyaltyLedgerEntryRepository.save(any())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `claimWelcomeStar awards one star under account row lock`() {
        val account = CustomerLoyaltyAccount(customerId = customerId, providerId = providerId)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderIdForUpdate(customerId, providerId))
            .thenReturn(Optional.of(account))

        val result = service.claimWelcomeStar(customerId, providerId)

        assertEquals(1, result.starBalance)
        assertTrue(result.welcomeStarClaimed)
        verify(customerLoyaltyAccountRepository).ensureAccount(customerId, providerId)
        verify(customerLoyaltyAccountRepository).findByCustomerIdAndProviderIdForUpdate(customerId, providerId)
        verify(loyaltyLedgerEntryRepository).save(check<LoyaltyLedgerEntry> {
            assertEquals(1, it.deltaStars)
            assertEquals(LedgerEntryType.WELCOME_STAR, it.entryType)
        })
    }

    @Test
    fun `claimWelcomeStar double tap returns same balance without crediting twice`() {
        val existingAccount = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 1,
            totalStarsEarned = 1,
            welcomeStarClaimed = true
        )
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderIdForUpdate(customerId, providerId))
            .thenReturn(Optional.of(existingAccount))

        val result = service.claimWelcomeStar(customerId, providerId)

        assertEquals(1, result.starBalance)
        assertTrue(result.welcomeStarClaimed)
        verify(loyaltyLedgerEntryRepository, never()).save(any())
    }

    @Test
    fun `processOrderDeliveredEvent atomically claims event and credits star`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(customerId = customerId, providerId = providerId, starBalance = 2)
        whenever(loyaltyProcessedEventRepository.insertIfAbsent("ORDER_DELIVERED", orderId)).thenReturn(1)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderIdForUpdate(customerId, providerId))
            .thenReturn(Optional.of(account))

        val processed = service.processOrderDeliveredEvent(orderId, customerId, providerId, BigDecimal("250.00"))

        assertTrue(processed)
        assertEquals(3, account.starBalance)
        verify(loyaltyLedgerEntryRepository).save(check<LoyaltyLedgerEntry> {
            assertEquals(1, it.deltaStars)
            assertEquals(LedgerEntryType.PURCHASE_STAR, it.entryType)
        })
    }

    @Test
    fun `duplicate delivered event is ignored before account mutation`() {
        val orderId = UUID.randomUUID()
        whenever(loyaltyProcessedEventRepository.insertIfAbsent("ORDER_DELIVERED", orderId)).thenReturn(0)

        val processed = service.processOrderDeliveredEvent(orderId, customerId, providerId, BigDecimal("250.00"))

        assertFalse(processed)
        verify(customerLoyaltyAccountRepository, never()).findByCustomerIdAndProviderIdForUpdate(any(), any())
        verify(loyaltyLedgerEntryRepository, never()).save(any())
    }

    @Test
    fun `processOrderDeliveredEvent ten star rollover issues reward`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(customerId = customerId, providerId = providerId, starBalance = 9)
        whenever(loyaltyProcessedEventRepository.insertIfAbsent("ORDER_DELIVERED", orderId)).thenReturn(1)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderIdForUpdate(customerId, providerId))
            .thenReturn(Optional.of(account))
        whenever(loyaltyRewardInstanceRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<LoyaltyRewardInstance>(0).apply { rewardId = UUID.randomUUID() }
        }

        val processed = service.processOrderDeliveredEvent(orderId, customerId, providerId, BigDecimal("250.00"))

        assertTrue(processed)
        assertEquals(0, account.starBalance)
        verify(loyaltyRewardInstanceRepository).save(check<LoyaltyRewardInstance> {
            assertEquals(BigDecimal("50.00"), it.rewardAmount)
            assertEquals(RewardStatus.ISSUED, it.status)
        })
    }

    @Test
    fun `processOrderRefundEvent atomically reverses star and locks reward before revoke`() {
        val orderId = UUID.randomUUID()
        val account = CustomerLoyaltyAccount(customerId = customerId, providerId = providerId, starBalance = 1, totalStarsEarned = 1)
        whenever(loyaltyProcessedEventRepository.insertIfAbsent("ORDER_REFUNDED", orderId)).thenReturn(1)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderIdForUpdate(customerId, providerId))
            .thenReturn(Optional.of(account))
        val activeReward = reward("RWD-TEST", RewardStatus.ISSUED)
        whenever(loyaltyRewardInstanceRepository.findByCustomerIdAndProviderIdAndStatusIn(customerId, providerId, listOf(RewardStatus.ISSUED)))
            .thenReturn(listOf(activeReward))
        whenever(loyaltyRewardInstanceRepository.findByCodeForUpdate("RWD-TEST")).thenReturn(Optional.of(activeReward))

        val processed = service.processOrderRefundEvent(orderId, customerId, providerId)

        assertTrue(processed)
        assertEquals(0, account.starBalance)
        verify(loyaltyLedgerEntryRepository).save(check<LoyaltyLedgerEntry> {
            assertEquals(-1, it.deltaStars)
            assertEquals(LedgerEntryType.STAR_REVERSAL, it.entryType)
        })
        verify(loyaltyRewardInstanceRepository).findByCodeForUpdate("RWD-TEST")
        assertEquals(RewardStatus.REVOKED, activeReward.status)
    }

    @Test
    fun `reserveReward is idempotent for same order under reward row lock`() {
        val orderId = UUID.randomUUID()
        val reward = reward("RWD-RESERVE", RewardStatus.ISSUED)
        whenever(loyaltyRewardInstanceRepository.findByCodeForUpdate(reward.code)).thenReturn(Optional.of(reward))

        val first = service.reserveReward(reward.code, customerId, providerId, orderId)
        val second = service.reserveReward(reward.code, customerId, providerId, orderId)

        assertSame(first, second)
        assertEquals(RewardStatus.RESERVED, reward.status)
        assertEquals(orderId, reward.orderId)
        verify(loyaltyRewardInstanceRepository, times(2)).findByCodeForUpdate(reward.code)
        verify(loyaltyRewardInstanceRepository, times(1)).save(reward)
    }

    @Test
    fun `reserveReward cannot move existing reservation to another order`() {
        val firstOrder = UUID.randomUUID()
        val reward = reward("RWD-OTHER", RewardStatus.RESERVED).apply { orderId = firstOrder }
        whenever(loyaltyRewardInstanceRepository.findByCodeForUpdate(reward.code)).thenReturn(Optional.of(reward))

        assertThrows<IllegalStateException> {
            service.reserveReward(reward.code, customerId, providerId, UUID.randomUUID())
        }

        verify(loyaltyRewardInstanceRepository, never()).save(any())
    }

    @Test
    fun `redeemReward is idempotent for same order and rejects different order`() {
        val orderId = UUID.randomUUID()
        val reward = reward("RWD-REDEEM", RewardStatus.RESERVED).apply { this.orderId = orderId }
        whenever(loyaltyRewardInstanceRepository.findByCodeForUpdate(reward.code)).thenReturn(Optional.of(reward))

        val first = service.redeemReward(reward.code, customerId, orderId)
        val replay = service.redeemReward(reward.code, customerId, orderId)

        assertSame(first, replay)
        assertEquals(RewardStatus.REDEEMED, reward.status)
        verify(loyaltyRewardInstanceRepository, times(1)).save(reward)

        assertThrows<IllegalStateException> {
            service.redeemReward(reward.code, customerId, UUID.randomUUID())
        }
        verify(loyaltyRewardInstanceRepository, times(3)).findByCodeForUpdate(reward.code)
    }

    @Test
    fun `releaseReward refuses stale order from clearing another reservation`() {
        val reservedOrder = UUID.randomUUID()
        val reward = reward("RWD-RELEASE", RewardStatus.RESERVED).apply { orderId = reservedOrder }
        whenever(loyaltyRewardInstanceRepository.findByCodeForUpdate(reward.code)).thenReturn(Optional.of(reward))

        assertThrows<IllegalStateException> {
            service.releaseRewardReservation(reward.code, customerId, UUID.randomUUID())
        }

        assertEquals(RewardStatus.RESERVED, reward.status)
        assertEquals(reservedOrder, reward.orderId)
        verify(loyaltyRewardInstanceRepository, never()).save(any())
    }

    private fun reward(code: String, status: RewardStatus) = LoyaltyRewardInstance(
        rewardId = UUID.randomUUID(),
        customerId = customerId,
        providerId = providerId,
        rewardAmount = BigDecimal("50.00"),
        status = status,
        code = code,
        expiresAt = Instant.now().plusSeconds(3600)
    )
}
