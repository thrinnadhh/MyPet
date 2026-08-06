package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class HostedCheckoutControllerTests {
    private val repository: TransactionRepository = mock()
    private val cashfreeGatewayService: CashfreeGatewayService = mock()
    private val service = HostedCheckoutService(
        transactionRepository = repository,
        cashfreeGatewayService = cashfreeGatewayService,
        checkoutTokenSecret = "checkout-secret-with-sufficient-entropy",
        cashfreeWebhookSecret = "",
        cashfreeClientSecret = "cashfree-client-secret",
        sandboxMode = true,
    )

    @Test
    fun `owner receives signed checkout session and can resolve it`() {
        val userId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        val transaction = Transaction(
            transactionId = transactionId,
            userId = userId,
            transactionType = "ORDER_PAYMENT",
            referenceId = UUID.randomUUID(),
            amount = BigDecimal("499.00"),
            status = "PENDING",
            gateway = "CASHFREE",
            gatewayTransactionId = "mypet_order_123",
        )
        whenever(repository.findById(transactionId)).thenReturn(Optional.of(transaction))
        whenever(cashfreeGatewayService.fetchPaymentSession("mypet_order_123", transactionId))
            .thenReturn("session_123")

        val session = service.createSession(transactionId, userId.toString(), "CUSTOMER")
        assertTrue(session.checkoutPath.startsWith("/api/v1/payments/checkout/$transactionId?"))

        val query = session.checkoutPath.substringAfter('?').split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to value
        }
        val resolved = service.resolve(
            transactionId,
            query.getValue("expires").toLong(),
            query.getValue("token"),
        )

        assertEquals("mypet_order_123", resolved.cashfreeOrderId)
        assertEquals("session_123", resolved.paymentSessionId)
        assertEquals("sandbox", resolved.environment)
    }

    @Test
    fun `different customer cannot create checkout session`() {
        val ownerId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        whenever(repository.findById(transactionId)).thenReturn(
            Optional.of(
                Transaction(
                    transactionId = transactionId,
                    userId = ownerId,
                    transactionType = "ORDER_PAYMENT",
                    referenceId = UUID.randomUUID(),
                    amount = BigDecimal("100.00"),
                    status = "PENDING",
                    gateway = "CASHFREE",
                    gatewayTransactionId = "mypet_order_456",
                ),
            ),
        )

        assertThrows<PaymentAccessDeniedException> {
            service.createSession(transactionId, UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `non pending transaction cannot open checkout`() {
        val userId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        whenever(repository.findById(transactionId)).thenReturn(
            Optional.of(
                Transaction(
                    transactionId = transactionId,
                    userId = userId,
                    transactionType = "ORDER_PAYMENT",
                    referenceId = UUID.randomUUID(),
                    amount = BigDecimal("100.00"),
                    status = "SUCCESS",
                    gateway = "CASHFREE",
                    gatewayTransactionId = "mypet_order_789",
                ),
            ),
        )

        assertThrows<IllegalStateException> {
            service.createSession(transactionId, userId.toString(), "CUSTOMER")
        }
    }

    @Test
    fun `Razorpay transaction cannot open Cashfree checkout`() {
        val userId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        whenever(repository.findById(transactionId)).thenReturn(
            Optional.of(
                Transaction(
                    transactionId = transactionId,
                    userId = userId,
                    transactionType = "ORDER_PAYMENT",
                    referenceId = UUID.randomUUID(),
                    amount = BigDecimal("100.00"),
                    status = "PENDING",
                    gateway = "RAZORPAY",
                    gatewayTransactionId = "order_legacy",
                ),
            ),
        )

        assertThrows<IllegalStateException> {
            service.createSession(transactionId, userId.toString(), "CUSTOMER")
        }
    }
}
