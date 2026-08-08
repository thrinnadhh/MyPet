package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.model.LedgerEntryType
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.repository.CustomerLoyaltyAccountRepository
import com.pawsnearme.paymentservice.repository.LoyaltyLedgerEntryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class LoyaltyReconciliationServiceTests {
    private val accountRepository: CustomerLoyaltyAccountRepository = mock()
    private val ledgerRepository: LoyaltyLedgerEntryRepository = mock()
    private val service = LoyaltyReconciliationService(accountRepository, ledgerRepository)
    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()

    @Test
    fun `reconciliation includes cycle rollover debit and does not restore spent stars`() {
        val account = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 10,
        )
        whenever(accountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(ledgerRepository.findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId, providerId))
            .thenReturn(
                listOf(
                    entry(1, LedgerEntryType.WELCOME_STAR),
                    entry(9, LedgerEntryType.PURCHASE_STAR),
                    entry(-10, LedgerEntryType.CYCLE_ROLLOVER),
                )
            )

        val balance = service.reconcile(customerId, providerId)

        assertEquals(0, balance)
        assertEquals(0, account.starBalance)
        verify(accountRepository).save(account)
    }

    @Test
    fun `reconciliation preserves post-rollover stars`() {
        val account = CustomerLoyaltyAccount(
            customerId = customerId,
            providerId = providerId,
            starBalance = 0,
        )
        whenever(accountRepository.findByCustomerIdAndProviderId(customerId, providerId))
            .thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(ledgerRepository.findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId, providerId))
            .thenReturn(
                listOf(
                    entry(1, LedgerEntryType.WELCOME_STAR),
                    entry(9, LedgerEntryType.PURCHASE_STAR),
                    entry(-10, LedgerEntryType.CYCLE_ROLLOVER),
                    entry(1, LedgerEntryType.PURCHASE_STAR),
                )
            )

        val balance = service.reconcile(customerId, providerId)

        assertEquals(1, balance)
        assertEquals(1, account.starBalance)
    }

    private fun entry(delta: Int, type: LedgerEntryType) = LoyaltyLedgerEntry(
        customerId = customerId,
        providerId = providerId,
        deltaStars = delta,
        entryType = type,
    )
}
