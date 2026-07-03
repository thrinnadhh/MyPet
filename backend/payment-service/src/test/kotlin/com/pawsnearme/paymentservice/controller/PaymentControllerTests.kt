package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.service.PaymentResultRequest
import com.pawsnearme.paymentservice.service.PaymentService
import com.pawsnearme.paymentservice.service.RazorpayOrderResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID

class PaymentControllerTests {

    private val paymentService: PaymentService = mock()
    private val controller = PaymentController(paymentService)

    private val userId = UUID.randomUUID()
    private val referenceId = UUID.randomUUID()

    @Test
    fun `createRazorpayOrder - mismatch user and role - throws PaymentAccessDeniedException`() {
        val request = CreateRazorpayOrderRequest(userId, referenceId, BigDecimal("500.00"), "ORDER_PAYMENT")

        assertThrows<PaymentAccessDeniedException> {
            controller.createRazorpayOrder(request, UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `createRazorpayOrder - matching user - succeeds`() {
        val request = CreateRazorpayOrderRequest(userId, referenceId, BigDecimal("500.00"), "ORDER_PAYMENT")
        val expected = RazorpayOrderResponse("key_123", "order_123", BigDecimal("500.00"), "INR", UUID.randomUUID())
        whenever(paymentService.createRazorpayOrder(userId, referenceId, BigDecimal("500.00"), "ORDER_PAYMENT")).thenReturn(expected)

        val response = controller.createRazorpayOrder(request, userId.toString(), "CUSTOMER")
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(expected, response.body)
    }

    @Test
    fun `handleWebhook - missing signature - throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            controller.handleWebhook("payload", null)
        }
    }

    @Test
    fun `getTransaction - user mismatch - throws PaymentAccessDeniedException`() {
        val txId = UUID.randomUUID()
        val tx = Transaction(
            transactionId = txId,
            userId = userId,
            transactionType = "ORDER_PAYMENT",
            referenceId = referenceId,
            amount = BigDecimal("500.00"),
            status = "PENDING"
        )
        whenever(paymentService.getTransactionById(txId)).thenReturn(tx)

        assertThrows<PaymentAccessDeniedException> {
            controller.getTransaction(txId, UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `refundPayment - non-admin role - throws PaymentAccessDeniedException`() {
        assertThrows<PaymentAccessDeniedException> {
            controller.refundPayment(referenceId, "CUSTOMER")
        }
    }

    @Test
    fun `refundPayment - admin role - succeeds`() {
        val tx = Transaction(
            transactionId = UUID.randomUUID(),
            userId = userId,
            transactionType = "ORDER_PAYMENT",
            referenceId = referenceId,
            amount = BigDecimal("500.00"),
            status = "REFUNDED"
        )
        whenever(paymentService.refundPayment(referenceId)).thenReturn(tx)

        val response = controller.refundPayment(referenceId, "ADMIN")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(tx, response.body)
    }
}
