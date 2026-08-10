package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderActor
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class OrderPaymentEventListenerTests {
    private val repository: OrderRepository = mock()
    private val orderService: OrderService = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val listener = OrderPaymentEventListener(ObjectMapper(), repository, orderService, paymentModule)

    @Test
    fun `captured payment confirms payment only and leaves lifecycle placed`() {
        val transactionId = UUID.randomUUID()
        val order = order(status = OrderStatus.PLACED, paymentStatus = PaymentStatus.PENDING)
        whenever(repository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(orderService.confirmOrder(order.orderId!!, transactionId)).thenAnswer {
            order.paymentId = transactionId
            order.paymentStatus = PaymentStatus.SUCCESS
            order
        }

        listener.handlePayload(event(order, "PaymentCaptured", transactionId))

        assertEquals(OrderStatus.PLACED, order.status)
        assertEquals(PaymentStatus.SUCCESS, order.paymentStatus)
        verify(orderService).confirmOrder(order.orderId!!, transactionId)
        verify(paymentModule, never()).refundOrder(any())
    }

    @Test
    fun `failed payment marks order payment failed and cancels placed order`() {
        val transactionId = UUID.randomUUID()
        val order = order(status = OrderStatus.PLACED, paymentStatus = PaymentStatus.PENDING)
        whenever(repository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(repository.saveAndFlush(order)).thenReturn(order)

        listener.handlePayload(event(order, "PaymentFailed", transactionId, "Cashfree failed"))

        assertEquals(PaymentStatus.FAILED, order.paymentStatus)
        verify(orderService).updateOrderStatus(
            eq(order.orderId!!),
            eq(OrderStatus.CANCELLED),
            eq(order.customerId),
            eq(OrderActor.CUSTOMER),
            eq("Cashfree failed"),
        )
    }

    @Test
    fun `late capture on cancelled order is immediately refunded`() {
        val transactionId = UUID.randomUUID()
        val order = order(status = OrderStatus.CANCELLED, paymentStatus = PaymentStatus.FAILED)
        whenever(repository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(repository.saveAndFlush(order)).thenReturn(order)

        listener.handlePayload(event(order, "PaymentCaptured", transactionId))

        assertEquals(PaymentStatus.REFUND_PENDING, order.paymentStatus)
        verify(paymentModule).refundOrder(order.orderId!!)
        verify(orderService, never()).confirmOrder(any(), any())
    }

    @Test
    fun `refund event closes order payment reconciliation`() {
        val transactionId = UUID.randomUUID()
        val order = order(status = OrderStatus.CANCELLED, paymentStatus = PaymentStatus.REFUND_PENDING)
        whenever(repository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(repository.saveAndFlush(order)).thenReturn(order)

        listener.handlePayload(event(order, "PaymentRefunded", transactionId))

        assertEquals(PaymentStatus.REFUNDED, order.paymentStatus)
    }

    private fun order(status: OrderStatus, paymentStatus: PaymentStatus) = Order(
        orderId = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = status,
        subtotalAmount = BigDecimal("450.00"),
        deliveryFee = BigDecimal("25.43"),
        taxAmount = BigDecimal("23.57"),
        totalAmount = BigDecimal("499.00"),
        paymentMethod = "UPI",
        paymentStatus = paymentStatus,
    )

    private fun event(
        order: Order,
        eventType: String,
        transactionId: UUID,
        reason: String? = null,
    ): String = ObjectMapper().writeValueAsString(
        OrderPaymentLifecycleEvent(
            eventId = UUID.randomUUID(),
            eventType = eventType,
            transactionId = transactionId,
            referenceId = order.orderId!!,
            transactionType = "ORDER_PAYMENT",
            actorId = order.customerId,
            amount = order.totalAmount,
            gateway = "CASHFREE",
            gatewayTransactionId = "cf-1",
            reason = reason,
        )
    )
}
