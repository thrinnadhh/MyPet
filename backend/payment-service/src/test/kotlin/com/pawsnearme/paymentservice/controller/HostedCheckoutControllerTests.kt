package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
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
    private val service = HostedCheckoutService(
        transactionRepository = repository,
        checkoutTokenSecret = "checkout-secret-with-sufficient-entropy",
        webhookSecret = "",
        razorpayKeyId = "rzp_test_123"
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
            gatewayTransactionId = "order_test_123"
        )
        whenever(repository.findById(transactionId)).thenReturn(Optional.of(transaction))

        val session = service.createSession(transactionId, userId.toString(), "CUSTOMER")
        assertTrue(session.checkoutPath.startsWith("/api/v1/payments/checkout/$transactionId?"))

        val query = session.checkoutPath.substringAfter('?').split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to value
        }
        val resolved = service.resolve(
            transactionId,
            query.getValue("expires").toLong(),
            query.getValue("token")
        )

        assertEquals("order_test_123", resolved.razorpayOrderId)
        assertEquals("rzp_test_123", resolved.keyId)
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
                    gatewayTransactionId = "order_test_456"
                )
            )
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
                    gatewayTransactionId = "order_test_789"
                )
            )
        )

        assertThrows<IllegalStateException> {
            service.createSession(transactionId, userId.toString(), "CUSTOMER")
        }
    }
}
