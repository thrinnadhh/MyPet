package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import com.pawsnearme.paymentservice.service.CashfreeOrderResponse
import com.pawsnearme.paymentservice.service.CashfreeRefundLifecycleService
import com.pawsnearme.paymentservice.service.CouponReservationLifecycleService
import com.pawsnearme.paymentservice.service.CreateCashfreeOrderRequest
import com.pawsnearme.paymentservice.service.PaymentService
import com.pawsnearme.paymentservice.service.RegisterLinkedAccountRequest
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
    private val cashfreeGatewayService: CashfreeGatewayService = mock()
    private val cashfreeRefundLifecycleService: CashfreeRefundLifecycleService = mock()
    private val couponReservationLifecycleService: CouponReservationLifecycleService = mock()
    private val controller = PaymentController(
        paymentService,
        cashfreeGatewayService,
        cashfreeRefundLifecycleService,
        couponReservationLifecycleService,
    )

    private val userId = UUID.randomUUID()
    private val referenceId = UUID.randomUUID()

    private fun request() = CreateCashfreeOrderRequest(
        userId = userId,
        referenceId = referenceId,
        amount = BigDecimal("500.00"),
        transactionType = "ORDER_PAYMENT",
        customerPhone = "9876543210",
        customerEmail = "customer@example.com",
        customerName = "Customer",
    )

    @Test
    fun `createCashfreeOrder - mismatch user and role - throws PaymentAccessDeniedException`() {
        assertThrows<PaymentAccessDeniedException> {
            controller.createCashfreeOrder(request(), UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `createCashfreeOrder - matching user - succeeds`() {
        val expected = CashfreeOrderResponse(
            orderId = "mypet_order_123",
            paymentSessionId = "session_123",
            amount = BigDecimal("500.00"),
            currency = "INR",
            transactionId = UUID.randomUUID(),
            environment = "SANDBOX",
        )
        whenever(cashfreeGatewayService.createOrder(request())).thenReturn(expected)

        val response = controller.createCashfreeOrder(request(), userId.toString(), "CUSTOMER")
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(expected, response.body)
    }

    @Test
    fun `handleWebhook - missing signature - throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            controller.handleWebhook("payload", null, "1720000000000")
        }
    }

    @Test
    fun `handleWebhook - missing timestamp - throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            controller.handleWebhook("payload", "signature", null)
        }
    }

    @Test
    fun `handleWebhook routes signed events through refund-aware lifecycle`() {
        whenever(
            cashfreeRefundLifecycleService.processWebhook("payload", "signature", "1720000000000", "event-1")
        ).thenReturn(true)

        val response = controller.handleWebhook("payload", "signature", "1720000000000", "event-1")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("processed", (response.body as Map<*, *>)["status"])
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
            status = "PENDING",
            gateway = "CASHFREE",
        )
        whenever(paymentService.getTransactionById(txId)).thenReturn(tx)

        assertThrows<PaymentAccessDeniedException> {
            controller.getTransaction(txId, UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `linked account onboarding fails closed until Cashfree Easy Split is active`() {
        val linkedAccountRequest = RegisterLinkedAccountRequest(
            payeeUserId = userId,
            payeeRole = "MERCHANT",
            accountNumber = "123456789012",
            ifsc = "SBIN0000001",
            businessName = "MyPet Merchant",
            email = "merchant@example.com",
        )

        val response = controller.registerLinkedAccount(
            linkedAccountRequest,
            userId.toString(),
            "MERCHANT",
        )

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(
            "CASHFREE_EASY_SPLIT_NOT_ACTIVE",
            (response.body as Map<*, *>)["code"],
        )
    }

    @Test
    fun `refundPayment - non-admin role - throws PaymentAccessDeniedException`() {
        assertThrows<PaymentAccessDeniedException> {
            controller.refundPayment(referenceId, "CUSTOMER", userId.toString())
        }
    }

    @Test
    fun `refundPayment - admin without identity - throws PaymentAccessDeniedException`() {
        assertThrows<PaymentAccessDeniedException> {
            controller.refundPayment(referenceId, "ADMIN", null)
        }
    }

    @Test
    fun `refundPayment - admin role and identity - succeeds`() {
        val tx = Transaction(
            transactionId = UUID.randomUUID(),
            userId = userId,
            transactionType = "ORDER_PAYMENT",
            referenceId = referenceId,
            amount = BigDecimal("500.00"),
            status = "REFUNDED",
            gateway = "CASHFREE",
        )
        whenever(cashfreeGatewayService.refundOrder(referenceId)).thenReturn(tx)

        val response = controller.refundPayment(referenceId, "ADMIN", UUID.randomUUID().toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(tx, response.body)
    }
}
