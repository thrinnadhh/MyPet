package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
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

        whenever(customerLoyaltyAccountRepository.save(any())).thenAnswer { invocation ->
            val acc = invocation.getArgument<CustomerLoyaltyAccount>(0)
            whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(acc.customerId, acc.providerId))
                .thenReturn(Optional.of(acc))
            acc
        }
    }

    @Test
    fun `claimWelcomeStar - awards 1 star and sets welcomeStarClaimed`() {
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.empty())

        val result = service.claimWelcomeStar(customerId, providerId)

        assertEquals(1, result.starBalance)
        assertTrue(result.welcomeStarClaimed)
        verify(loyaltyLedgerEntryRepository).save(check<LoyaltyLedgerEntry> {
            assertEquals(1, it.deltaStars)
            assertEquals(LedgerEntryType.WELCOME_STAR, it.entryType)
        })
    }

    @Test
    fun `claimWelcomeStar - double-tap returns same balance without crediting twice`() {
        val existingAccount = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 1,
            welcomeStarClaimed = true
        )
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.of(existingAccount))

        val result = service.claimWelcomeStar(customerId, providerId)

        assertEquals(1, result.starBalance)
        assertTrue(result.welcomeStarClaimed)
        verify(loyaltyLedgerEntryRepository, never()).save(any())
    }

    @Test
    fun `processOrderDeliveredEvent - credits star when order amount meets minimum`() {
        val orderId = UUID.randomUUID()
        whenever(loyaltyProcessedEventRepository.existsByEventTypeAndReferenceId("ORDER_DELIVERED", orderId)).thenReturn(false)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.of(CustomerLoyaltyAccount(customerId = customerId, providerId = providerId, starBalance = 2)))

        val processed = service.processOrderDeliveredEvent(orderId, customerId, providerId, BigDecimal("250.00"))

        assertTrue(processed)
        verify(loyaltyLedgerEntryRepository).save(check<LoyaltyLedgerEntry> {
            assertEquals(1, it.deltaStars)
            assertEquals(LedgerEntryType.PURCHASE_STAR, it.entryType)
        })
    }

    @Test
    fun `processOrderDeliveredEvent - 10 star rollover issues reward`() {
        val orderId = UUID.randomUUID()
        whenever(loyaltyProcessedEventRepository.existsByEventTypeAndReferenceId("ORDER_DELIVERED", orderId)).thenReturn(false)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.of(CustomerLoyaltyAccount(customerId = customerId, providerId = providerId, starBalance = 9)))

        whenever(loyaltyRewardInstanceRepository.save(any())).thenAnswer { invocation ->
            val r = invocation.getArgument<LoyaltyRewardInstance>(0)
            r.rewardId = UUID.randomUUID()
            r
        }

        val processed = service.processOrderDeliveredEvent(orderId, customerId, providerId, BigDecimal("250.00"))

        assertTrue(processed)
        verify(loyaltyRewardInstanceRepository).save(check<LoyaltyRewardInstance> {
            assertEquals(BigDecimal("50.00"), it.rewardAmount)
            assertEquals(RewardStatus.ISSUED, it.status)
        })
    }

    @Test
    fun `processOrderRefundEvent - reverses star and revokes active reward`() {
        val orderId = UUID.randomUUID()
        whenever(loyaltyProcessedEventRepository.existsByEventTypeAndReferenceId("ORDER_REFUNDED", orderId)).thenReturn(false)
        whenever(customerLoyaltyAccountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.of(CustomerLoyaltyAccount(customerId = customerId, providerId = providerId, starBalance = 1)))

        val activeReward = LoyaltyRewardInstance(
            rewardId = UUID.randomUUID(),
            customerId = customerId,
            providerId = providerId,
            rewardAmount = BigDecimal("50.00"),
            status = RewardStatus.ISSUED,
            code = "RWD-TEST"
        )
        whenever(loyaltyRewardInstanceRepository.findByCustomerIdAndProviderIdAndStatusIn(customerId, providerId, listOf(RewardStatus.ISSUED)))
            .thenReturn(listOf(activeReward))

        val processed = service.processOrderRefundEvent(orderId, customerId, providerId)

        assertTrue(processed)
        verify(loyaltyLedgerEntryRepository).save(check<LoyaltyLedgerEntry> {
            assertEquals(-1, it.deltaStars)
            assertEquals(LedgerEntryType.STAR_REVERSAL, it.entryType)
        })
        assertEquals(RewardStatus.REVOKED, activeReward.status)
    }
}
