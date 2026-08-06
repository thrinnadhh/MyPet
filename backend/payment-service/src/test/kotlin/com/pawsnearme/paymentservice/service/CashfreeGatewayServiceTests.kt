package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.paymentservice.model.OrderRef
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.OrderRefRepository
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class CashfreeGatewayServiceTests {
    private val transactions: TransactionRepository = mock()
    private val orders: OrderRefRepository = mock()
    private val idempotency: IdempotencyService = mock()
    private val secret = "cashfree-test-secret"
    private val service = CashfreeGatewayService(
        transactionRepository = transactions,
        orderRefRepository = orders,
        idempotencyService = idempotency,
        objectMapper = ObjectMapper(),
        clientId = "",
        clientSecret = "",
        configuredWebhookSecret = secret,
        apiVersion = "2025-01-01",
        sandboxMode = true,
    )

    private val customerId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @Test
    fun `sandbox order creation uses server authoritative order and Cashfree identifiers`() {
        whenever(orders.findById(orderId)).thenReturn(
            Optional.of(
                OrderRef(
                    orderId = orderId,
                    providerId = UUID.randomUUID(),
                    customerId = customerId,
                    captainId = null,
                    status = "PLACED",
                    totalAmount = BigDecimal("499.00"),
                    deliveredAt = null,
                ),
            ),
        )
        whenever(transactions.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(any(), any()))
            .thenReturn(null)
        whenever(transactions.save(any())).thenAnswer { invocation ->
            invocation.getArgument<Transaction>(0).also {
                it.transactionId = it.transactionId ?: UUID.randomUUID()
            }
        }

        val result = service.createOrder(
            CreateCashfreeOrderRequest(
                userId = customerId,
                referenceId = orderId,
                amount = BigDecimal("499.00"),
                transactionType = "ORDER_PAYMENT",
                customerPhone = "9876543210",
            ),
        )

        assertTrue(result.orderId.startsWith("mypet_"))
        assertTrue(result.paymentSessionId.startsWith("session_mock_"))
        assertEquals("CASHFREE", result.environment.removeSuffix("SANDBOX").let { if (it.isEmpty()) "CASHFREE" else it })
        assertEquals(BigDecimal("499.00"), result.amount)
    }

    @Test
    fun `webhook signature uses timestamp plus exact raw body`() {
        val body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}"
        val timestamp = Instant.now().toEpochMilli().toString()
        val signature = signature(timestamp, body)

        assertTrue(service.verifyWebhookSignature(body, timestamp, signature))
        assertFalse(service.verifyWebhookSignature("$body ", timestamp, signature))
    }

    @Test
    fun `stale webhook timestamp is rejected`() {
        val body = "{}"
        val timestamp = Instant.now().minusSeconds(601).toEpochMilli().toString()
        assertFalse(service.verifyWebhookSignature(body, timestamp, signature(timestamp, body)))
    }

    @Test
    fun `successful Cashfree webhook is idempotent and marks transaction successful`() {
        val transaction = Transaction(
            transactionId = UUID.randomUUID(),
            userId = customerId,
            transactionType = "ORDER_PAYMENT",
            referenceId = orderId,
            amount = BigDecimal("499.00"),
            status = "PENDING",
            gateway = "CASHFREE",
            gatewayTransactionId = "mypet_cashfree_order",
        )
        whenever(idempotency.checkAndRecord(any())).thenReturn(true)
        whenever(transactions.findByGatewayTransactionId("mypet_cashfree_order")).thenReturn(transaction)
        whenever(transactions.save(any())).thenAnswer { it.getArgument(0) }

        val body = """{"type":"PAYMENT_SUCCESS_WEBHOOK","event_time":"2026-08-06T00:00:00Z","data":{"order":{"order_id":"mypet_cashfree_order","order_amount":499.00,"order_currency":"INR"},"payment":{"cf_payment_id":"12345","payment_status":"SUCCESS","payment_amount":499.00,"payment_currency":"INR"}}}"""
        val timestamp = Instant.now().toEpochMilli().toString()

        assertTrue(service.processWebhook(body, signature(timestamp, body), timestamp, "cashfree-event-1"))
        assertEquals("SUCCESS", transaction.status)
    }

    private fun signature(timestamp: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(
            mac.doFinal((timestamp + body).toByteArray(StandardCharsets.UTF_8)),
        )
    }
}
