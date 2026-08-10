package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class PendingPaymentOrderWorkerTests {
    private val repository: TransactionRepository = mock()
    private val lifecycle: OrderPaymentLifecycleService = mock()

    @Test
    fun `worker expires stale pending order payments`() {
        val transaction = Transaction(
            transactionId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            transactionType = "ORDER_PAYMENT",
            referenceId = UUID.randomUUID(),
            amount = BigDecimal("499.00"),
            status = "PENDING",
            gateway = "CASHFREE",
        )
        whenever(
            repository.findTop100ByTransactionTypeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq("ORDER_PAYMENT"),
                eq("PENDING"),
                any(),
            )
        ).thenReturn(listOf(transaction))

        PendingPaymentOrderWorker(repository, lifecycle, Duration.ofMinutes(30)).run()

        verify(lifecycle).fail(
            eq(transaction),
            eq("Online payment was not completed within 30 minutes"),
            eq("PaymentExpired"),
        )
    }
}
