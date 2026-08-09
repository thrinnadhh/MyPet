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
    private val internalSecret = "integration-secret"
    private val controller = PaymentController(
        paymentService,
        cashfreeGatewayService,
        cashfreeRefundLifecycleService,
        couponReservationLifecycleService,
        internalSecret,
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
    fun `create order rejects mismatched customer`() {
        assertThrows<PaymentAccessDeniedException> { controller.createCashfreeOrder(request(), UUID.randomUUID().toString(), "CUSTOMER") }
    }

    @Test
    fun `create order accepts matching customer`() {
        val expected = CashfreeOrderResponse("mypet_order_123", "session_123", BigDecimal("500.00"), "INR", UUID.randomUUID(), "SANDBOX")
        whenever(cashfreeGatewayService.createOrder(request())).thenReturn(expected)
        assertEquals(HttpStatus.CREATED, controller.createCashfreeOrder(request(), userId.toString(), "CUSTOMER").statusCode)
    }

    @Test
    fun `webhook requires signature and timestamp`() {
        assertThrows<IllegalArgumentException> { controller.handleWebhook("payload", null, "1720000000000") }
        assertThrows<IllegalArgumentException> { controller.handleWebhook("payload", "signature", null) }
    }

    @Test
    fun `webhook routes through refund aware lifecycle`() {
        whenever(cashfreeRefundLifecycleService.processWebhook("payload", "signature", "1720000000000", "event-1")).thenReturn(true)
        val response = controller.handleWebhook("payload", "signature", "1720000000000", "event-1")
        assertEquals("processed", (response.body as Map<*, *>)["status"])
    }

    @Test
    fun `transaction lookup enforces customer ownership`() {
        val txId = UUID.randomUUID()
        whenever(paymentService.getTransactionById(txId)).thenReturn(
            Transaction(transactionId = txId, userId = userId, transactionType = "ORDER_PAYMENT", referenceId = referenceId, amount = BigDecimal("500.00"), status = "PENDING", gateway = "CASHFREE"),
        )
        assertThrows<PaymentAccessDeniedException> { controller.getTransaction(txId, UUID.randomUUID().toString(), "CUSTOMER") }
    }

    @Test
    fun `linked account onboarding fails closed until Cashfree Easy Split is active`() {
        val response = controller.registerLinkedAccount(
            RegisterLinkedAccountRequest(userId, "MERCHANT", "123456789012", "SBIN0000001", "MyPet Merchant", "merchant@example.com"),
            userId.toString(),
            "MERCHANT",
        )
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("CASHFREE_EASY_SPLIT_NOT_ACTIVE", (response.body as Map<*, *>)["code"])
    }

    @Test
    fun `direct refund rejects human admin credentials without internal service identity`() {
        assertThrows<PaymentAccessDeniedException> {
            controller.refundPayment(referenceId, "ADMIN", userId.toString())
        }
    }

    @Test
    fun `direct refund rejects wrong service even with shared secret`() {
        assertThrows<PaymentAccessDeniedException> {
            controller.refundPayment(referenceId, internalSecret, "merchant-service")
        }
    }

    @Test
    fun `trusted order service can execute domain approved refund`() {
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
        val response = controller.refundPayment(referenceId, internalSecret, "order-service")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(tx, response.body)
    }
}
