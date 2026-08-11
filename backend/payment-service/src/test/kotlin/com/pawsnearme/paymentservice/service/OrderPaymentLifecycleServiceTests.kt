package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.module.PrepareOrderPaymentCommand
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class OrderPaymentLifecycleServiceTests {
    private val repository: TransactionRepository = mock()
    private val outbox: OutboxService = mock()
    private val service = OrderPaymentLifecycleService(repository, outbox)

    @Test
    fun `prepare reuses existing pending server transaction`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val transaction = tx(orderId, customerId, "PENDING")
        whenever(repository.findFirstByReferenceIdOrderByCreatedAtDesc(orderId)).thenReturn(transaction)

        val result = service.prepare(PrepareOrderPaymentCommand(orderId, customerId, BigDecimal("499.00")))

        assertEquals(transaction.transactionId, result.transactionId)
        assertEquals("PENDING", result.status)
        verify(repository, never()).saveAndFlush(any())
    }

    @Test
    fun `capture publishes PaymentCaptured exactly once for replayed transaction`() {
        val transaction = tx(UUID.randomUUID(), UUID.randomUUID(), "PENDING")
        whenever(repository.saveAndFlush(transaction)).thenReturn(transaction)

        val first = service.capture(transaction)
        val second = service.capture(transaction)

        assertSame(transaction, first)
        assertSame(transaction, second)
        assertEquals("SUCCESS", transaction.status)
        verify(outbox).saveEvent(
            eventId = any(),
            aggregateType = eq("PAYMENT"),
            aggregateId = eq(transaction.referenceId),
            eventType = eq("PaymentCaptured"),
            eventPayload = any(),
        )
    }

    @Test
    fun `expiry marks pending payment expired and publishes deterministic event`() {
        val transaction = tx(UUID.randomUUID(), UUID.randomUUID(), "PENDING")
        whenever(repository.saveAndFlush(transaction)).thenReturn(transaction)

        val result = service.fail(transaction, "payment timeout", "PaymentExpired")

        assertEquals("EXPIRED", result.status)
        verify(outbox).saveEvent(
            eventId = any(),
            aggregateType = eq("PAYMENT"),
            aggregateId = eq(transaction.referenceId),
            eventType = eq("PaymentExpired"),
            eventPayload = any(),
        )
    }

    @Test
    fun `failure replay does not publish another event`() {
        val transaction = tx(UUID.randomUUID(), UUID.randomUUID(), "FAILED")

        service.fail(transaction, "same failure")

        verify(repository, never()).saveAndFlush(any())
        verify(outbox, never()).saveEvent(any(), any(), any(), any(), any())
    }

    private fun tx(orderId: UUID, customerId: UUID, status: String) = Transaction(
        transactionId = UUID.randomUUID(),
        userId = customerId,
        transactionType = "ORDER_PAYMENT",
        referenceId = orderId,
        amount = BigDecimal("499.00"),
        status = status,
        gateway = "CASHFREE",
    )
}
