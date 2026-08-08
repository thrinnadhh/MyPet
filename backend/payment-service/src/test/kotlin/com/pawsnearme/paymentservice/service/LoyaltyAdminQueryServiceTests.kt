package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.model.LoyaltyAuditLog
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.repository.CustomerLoyaltyAccountRepository
import com.pawsnearme.paymentservice.repository.LoyaltyAuditLogRepository
import com.pawsnearme.paymentservice.repository.LoyaltyLedgerEntryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.UUID

class LoyaltyAdminQueryServiceTests {
    private val accountRepository: CustomerLoyaltyAccountRepository = mock()
    private val ledgerRepository: LoyaltyLedgerEntryRepository = mock()
    private val auditRepository: LoyaltyAuditLogRepository = mock()
    private val service = LoyaltyAdminQueryService(accountRepository, ledgerRepository, auditRepository)

    @Test
    fun `admin loyalty accounts are bounded`() {
        whenever(accountRepository.findAll(any<Pageable>()))
            .thenAnswer { invocation -> PageImpl<CustomerLoyaltyAccount>(emptyList(), invocation.getArgument(0), 0L) }

        val result = service.accounts(0, 25)

        assertEquals(0L, result.totalElements)
        assertEquals(25, result.size)
    }

    @Test
    fun `customer ledger uses server-side pagination`() {
        val customerId = UUID.randomUUID()
        whenever(ledgerRepository.findByCustomerIdOrderByCreatedAtDesc(org.mockito.kotlin.eq(customerId), any<Pageable>()))
            .thenAnswer { invocation -> PageImpl<LoyaltyLedgerEntry>(emptyList(), invocation.getArgument(1), 0L) }

        val result = service.customerLedger(customerId, 0, 25)

        assertEquals(0L, result.totalElements)
        verify(ledgerRepository).findByCustomerIdOrderByCreatedAtDesc(org.mockito.kotlin.eq(customerId), any<Pageable>())
    }

    @Test
    fun `admin loyalty query rejects unbounded page size`() {
        assertThrows<IllegalArgumentException> { service.accounts(0, 1000) }
        verify(accountRepository, never()).findAll(any<Pageable>())
    }
}
