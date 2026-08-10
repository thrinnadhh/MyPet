package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.UUID

class OrderReleaseReconciliationServiceTests {
    private val orderRepository: OrderRepository = mock()
    private val itemRepository: OrderItemRepository = mock()
    private val catalogModule: CatalogModuleApi = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val service = OrderReleaseReconciliationService(
        ObjectMapper(), orderRepository, itemRepository, catalogModule, paymentModule
    )

    @Test
    fun `cancelled pending order restores exact lines releases discounts and expires payment`() {
        val order = order(OrderStatus.CANCELLED, PaymentStatus.PENDING).also {
            it.couponCode = "SAVE10"
            it.loyaltyRewardId = UUID.randomUUID()
        }
        val itemA = item(order.orderId!!, 2)
        val itemB = item(order.orderId!!, 1)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(itemRepository.findByOrderId(order.orderId!!)).thenReturn(listOf(itemA, itemB))

        service.reconcileRelease(order.orderId!!)

        val captor = argumentCaptor<StockMutationCommand>()
        verify(catalogModule, org.mockito.kotlin.times(2)).restoreStock(captor.capture())
        assertEquals(
            UUID.nameUUIDFromBytes("RESTORE:${order.orderId}:${itemA.orderItemId}".toByteArray(StandardCharsets.UTF_8)),
            captor.allValues[0].idempotencyKey,
        )
        assertEquals(
            UUID.nameUUIDFromBytes("RESTORE:${order.orderId}:${itemB.orderItemId}".toByteArray(StandardCharsets.UTF_8)),
            captor.allValues[1].idempotencyKey,
        )
        verify(paymentModule).releaseCoupon("SAVE10", order.customerId, order.orderId!!)
        verify(paymentModule).releaseLoyaltyReward(order.loyaltyRewardId!!, order.customerId, order.orderId!!)
        verify(paymentModule).expireOrderPayment(
            order.orderId!!,
            "Order cancelled before online payment completed",
        )
    }

    @Test
    fun `rejected paid order restores stock and starts refund`() {
        val order = order(OrderStatus.REJECTED, PaymentStatus.SUCCESS)
        val line = item(order.orderId!!, 1)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(itemRepository.findByOrderId(order.orderId!!)).thenReturn(listOf(line))
        whenever(orderRepository.saveAndFlush(order)).thenReturn(order)

        service.reconcileRelease(order.orderId!!)

        assertEquals(PaymentStatus.REFUND_PENDING, order.paymentStatus)
        verify(paymentModule).refundOrder(order.orderId!!)
    }

    @Test
    fun `delivery settles reserved loyalty reward`() {
        val order = order(OrderStatus.DELIVERED, PaymentStatus.COD_COLLECTED).also {
            it.loyaltyRewardId = UUID.randomUUID()
        }
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(Optional.of(order))

        service.settleDelivered(order.orderId!!)

        verify(paymentModule).redeemLoyaltyReward(
            order.loyaltyRewardId!!,
            order.customerId,
            order.orderId!!,
        )
    }

    @Test
    fun `replayed release uses the same restore operation identity`() {
        val order = order(OrderStatus.CANCELLED, PaymentStatus.FAILED)
        val line = item(order.orderId!!, 1)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(itemRepository.findByOrderId(order.orderId!!)).thenReturn(listOf(line))

        service.reconcileRelease(order.orderId!!)
        service.reconcileRelease(order.orderId!!)

        val captor = argumentCaptor<StockMutationCommand>()
        verify(catalogModule, org.mockito.kotlin.times(2)).restoreStock(captor.capture())
        assertEquals(captor.allValues[0].idempotencyKey, captor.allValues[1].idempotencyKey)
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

    private fun item(orderId: UUID, quantity: Int) = OrderItem(
        orderItemId = UUID.randomUUID(),
        orderId = orderId,
        offeringId = UUID.randomUUID(),
        offeringNameSnapshot = "Dog Food",
        unitPriceSnapshot = BigDecimal("100.00"),
        quantity = quantity,
        lineTotal = BigDecimal("100.00").multiply(BigDecimal(quantity)),
    )
}
