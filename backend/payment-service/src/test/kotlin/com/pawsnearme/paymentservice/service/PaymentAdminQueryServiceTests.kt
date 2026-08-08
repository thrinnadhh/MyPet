package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.AdminTransactionRepository
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
import java.time.Instant

class PaymentAdminQueryServiceTests {
    private val repository: AdminTransactionRepository = mock()
    private val service = PaymentAdminQueryService(repository)

    @Test
    fun `payment search uses bounded server pagination`() {
        whenever(repository.search(any(), any(), any(), any(), any<Pageable>()))
            .thenAnswer { invocation -> PageImpl<Transaction>(emptyList(), invocation.getArgument(4), 0L) }

        val result = service.search(null, null, "SUCCESS", null, null, 0, 25)

        assertEquals(0L, result.totalElements)
        assertEquals(25, result.size)
    }

    @Test
    fun `payment search rejects invalid date window before repository query`() {
        val now = Instant.now()
        assertThrows<IllegalArgumentException> {
            service.search(null, null, null, now, now.minusSeconds(1), 0, 25)
        }
        verify(repository, never()).search(any(), any(), any(), any(), any<Pageable>())
    }

    @Test
    fun `payment search rejects unbounded page size`() {
        assertThrows<IllegalArgumentException> {
            service.search(null, null, null, null, null, 0, 1000)
        }
        verify(repository, never()).search(any(), any(), any(), any(), any<Pageable>())
    }
}
