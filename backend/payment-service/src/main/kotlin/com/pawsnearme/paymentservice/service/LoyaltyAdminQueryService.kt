package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.model.LoyaltyAuditLog
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.repository.CustomerLoyaltyAccountRepository
import com.pawsnearme.paymentservice.repository.LoyaltyAuditLogRepository
import com.pawsnearme.paymentservice.repository.LoyaltyLedgerEntryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class LoyaltyAdminPage<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@Service
class LoyaltyAdminQueryService(
    private val accountRepository: CustomerLoyaltyAccountRepository,
    private val ledgerRepository: LoyaltyLedgerEntryRepository,
    private val auditRepository: LoyaltyAuditLogRepository
) {
    @Transactional(readOnly = true)
    fun accounts(page: Int, size: Int): LoyaltyAdminPage<CustomerLoyaltyAccount> {
        requirePage(page, size)
        val result = accountRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        )
        return LoyaltyAdminPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun customerLedger(customerId: UUID, page: Int, size: Int): LoyaltyAdminPage<LoyaltyLedgerEntry> {
        requirePage(page, size)
        val result = ledgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size))
        return LoyaltyAdminPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun audit(page: Int, size: Int): LoyaltyAdminPage<LoyaltyAuditLog> {
        requirePage(page, size)
        val result = auditRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        return LoyaltyAdminPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    private fun requirePage(page: Int, size: Int) {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
    }
}
