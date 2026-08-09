package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.util.UUID

class CashfreeRefundLifecycleServiceTests {
    private val transactions: TransactionRepository = mock()
    private val gateway: CashfreeGatewayService = mock()
    private val idempotency: IdempotencyService = mock()
    private val rest: RestOperations = mock()
    private val service = CashfreeRefundLifecycleService(
        transactionRepository = transactions,
        cashfreeGatewayService = gateway,
        idempotencyService = idempotency,
        objectMapper = ObjectMapper(),
        clientId = "client-id",
        clientSecret = "client-secret",
        apiVersion = "2025-01-01",
        sandboxMode = true,
        restTemplate = rest,
    )

    @Test
    fun `successful remote refund reconciliation marks transaction refunded`() {
        val transaction = refundTransaction("REFUND_PENDING")
        val refundId = expectedRefundId(transaction)
        whenever(
            transactions.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
                eq(transaction.referenceId),
                any(),
            ),
        ).thenReturn(transaction)
        whenever(
            rest.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<Any>>(),
                eq(Map::class.java),
            ),
        ).thenReturn(
            ResponseEntity.ok(
                mapOf(
                    "order_id" to transaction.gatewayTransactionId,
                    "refund_id" to refundId,
                    "refund_amount" to transaction.amount,
                    "refund_currency" to transaction.currency,
                    "refund_status" to "SUCCESS",
                ),
            ),
        )
        whenever(transactions.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.reconcileReference(transaction.referenceId)

        assertEquals("REFUNDED", result.status)
        verify(transactions).save(transaction)
    }

    @Test
    fun `failed remote refund reconciliation records explicit refund failure`() {
        val transaction = refundTransaction("REFUND_PENDING")
        val refundId = expectedRefundId(transaction)
        whenever(
            transactions.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
                eq(transaction.referenceId),
                any(),
            ),
        ).thenReturn(transaction)
        whenever(
            rest.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<Any>>(),
                eq(Map::class.java),
            ),
        ).thenReturn(
            ResponseEntity.ok(
                mapOf(
                    "order_id" to transaction.gatewayTransactionId,
                    "refund_id" to refundId,
                    "refund_amount" to transaction.amount,
                    "refund_currency" to transaction.currency,
                    "refund_status" to "FAILED",
                ),
            ),
        )
        whenever(transactions.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.reconcileReference(transaction.referenceId)

        assertEquals("REFUND_FAILED", result.status)
        verify(transactions).save(transaction)
    }

    @Test
    fun `refund webhook rejects a refund id that is not owned by transaction`() {
        val transaction = refundTransaction("REFUND_PENDING")
        whenever(gateway.verifyWebhookSignature(any(), any(), any())).thenReturn(true)
        whenever(transactions.findRefundByGatewayTransactionId(transaction.gatewayTransactionId!!))
            .thenReturn(transaction)

        val payload = """
            {
              "type":"REFUND_STATUS_WEBHOOK",
              "event_time":"2026-08-09T03:30:00Z",
              "data":{"refund":{"order_id":"${transaction.gatewayTransactionId}","refund_id":"attacker-refund"}}
            }
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            service.processWebhook(payload, "signature", "1720000000000", "event-1")
        }
        verify(idempotency, never()).checkAndRecord(any())
    }

    private fun refundTransaction(status: String) = Transaction(
        transactionId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        transactionType = "ORDER_PAYMENT",
        referenceId = UUID.randomUUID(),
        amount = BigDecimal("499.00"),
        currency = "INR",
        status = status,
        gateway = "CASHFREE",
        gatewayTransactionId = "mypet_order_${UUID.randomUUID()}",
    )

    private fun expectedRefundId(transaction: Transaction): String =
        "refund_${transaction.transactionId.toString().replace("-", "")}"
}
