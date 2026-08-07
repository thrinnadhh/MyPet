package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.repository.CustomerLoyaltyAccountRepository
import com.pawsnearme.paymentservice.repository.LoyaltyLedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Rebuilds the spendable star balance from the immutable ledger.
 * CYCLE_ROLLOVER is a real -targetStars debit and must be included; excluding it
 * would restore stars that were already exchanged for a reward.
 */
@Service
class LoyaltyReconciliationService(
    private val accountRepository: CustomerLoyaltyAccountRepository,
    private val ledgerRepository: LoyaltyLedgerEntryRepository,
) {
    @Transactional
    fun reconcile(customerId: UUID, providerId: UUID): Int {
        val ledgerBalance = ledgerRepository
            .findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId, providerId)
            .sumOf { it.deltaStars }
            .coerceAtLeast(0)

        val account = accountRepository.findByCustomerIdAndProviderId(customerId, providerId)
            .orElseGet {
                accountRepository.save(
                    CustomerLoyaltyAccount(
                        customerId = customerId,
                        providerId = providerId,
                        starBalance = ledgerBalance,
                    )
                )
            }

        if (account.starBalance != ledgerBalance) {
            account.starBalance = ledgerBalance
            account.updatedAt = Instant.now()
            accountRepository.save(account)
        }
        return account.starBalance
    }
}
