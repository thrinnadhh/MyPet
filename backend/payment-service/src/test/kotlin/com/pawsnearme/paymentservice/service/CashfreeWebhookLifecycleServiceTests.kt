package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class CashfreeWebhookLifecycleServiceTests {
    private val gateway: CashfreeGatewayService = mock()
    private val repository: TransactionRepository = mock()
    private val idempotencyService: IdempotencyService = mock()
    private val lifecycle: OrderPaymentLifecycleService = mock()
    private val service = CashfreeWebhookLifecycleService(
        gateway,
        repository,
        idempotencyService,
        ObjectMapper(),
        lifecycle,
    )

    @Test
    fun `duplicate Cashfree success webhook captures payment once`() {
        val transaction = transaction()
        whenever(gateway.verifyWebhookSignature(any(), eq("123"), eq("sig"))).thenReturn(true)
        whenever(repository.findByGatewayTransactionId("cf-order-1")).thenReturn(transaction)
        whenever(idempotencyService.checkAndRecord(any())).thenReturn(true, false)
        whenever(lifecycle.capture(transaction)).thenReturn(transaction)
        val body = webhook("PAYMENT_SUCCESS_WEBHOOK", "SUCCESS")

        assertTrue(service.process(body, "sig", "123", "same-event"))
        assertFalse(service.process(body, "sig", "123", "same-event"))

        verify(lifecycle).capture(transaction)
    }

    @Test
    fun `Cashfree failed webhook publishes deterministic failure`() {
        val transaction = transaction()
        whenever(gateway.verifyWebhookSignature(any(), eq("123"), eq("sig"))).thenReturn(true)
        whenever(repository.findByGatewayTransactionId("cf-order-1")).thenReturn(transaction)
        whenever(idempotencyService.checkAndRecord(any())).thenReturn(true)

        service.process(webhook("PAYMENT_FAILED_WEBHOOK", "FAILED"), "sig", "123", "failed-event")

        verify(lifecycle).fail(
            eq(transaction),
            eq("Cashfree reported payment failure (FAILED)"),
            eq("PaymentFailed"),
        )
    }

    @Test
    fun `Cashfree user dropped webhook becomes a payment failure event`() {
        val transaction = transaction()
        whenever(gateway.verifyWebhookSignature(any(), eq("123"), eq("sig"))).thenReturn(true)
        whenever(repository.findByGatewayTransactionId("cf-order-1")).thenReturn(transaction)
        whenever(idempotencyService.checkAndRecord(any())).thenReturn(true)

        service.process(webhook("PAYMENT_USER_DROPPED_WEBHOOK", "USER_DROPPED"), "sig", "123", "dropped-event")

        verify(lifecycle).fail(
            eq(transaction),
            eq("Customer abandoned the Cashfree payment flow"),
            eq("PaymentFailed"),
        )
    }

    private fun transaction() = Transaction(
        transactionId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        transactionType = "ORDER_PAYMENT",
        referenceId = UUID.randomUUID(),
        amount = BigDecimal("499.00"),
        status = "PENDING",
        gateway = "CASHFREE",
        gatewayTransactionId = "cf-order-1",
    )

    private fun webhook(type: String, paymentStatus: String) = """
        {
          "type": "$type",
          "event_time": "2026-08-10T10:00:00Z",
          "data": {
            "order": {
              "order_id": "cf-order-1",
              "order_amount": 499.00,
              "order_currency": "INR"
            },
            "payment": {
              "cf_payment_id": "cf-payment-1",
              "payment_status": "$paymentStatus",
              "payment_amount": 499.00,
              "payment_currency": "INR"
            }
          }
        }
    """.trimIndent()
}
