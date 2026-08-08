package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.OrderStatusHistory
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AdminOrderQueryServiceTests {
    private val orderRepository: OrderRepository = mock()
    private val itemRepository: OrderItemRepository = mock()
    private val historyRepository: OrderStatusHistoryRepository = mock()
    private val service = AdminOrderQueryService(orderRepository, itemRepository, historyRepository)

    @Test
    fun `admin order search is bounded and filterable`() {
        val order = order()
        whenever(
            orderRepository.searchForAdmin(
                eq(order.orderId),
                eq(order.customerId),
                eq(order.providerId),
                eq(order.paymentId),
                eq(OrderStatus.PLACED),
                any(),
                any(),
                any<Pageable>()
            )
        ).thenAnswer { invocation -> PageImpl(listOf(order), invocation.getArgument(7), 1L) }

        val page = service.search(
            orderId = order.orderId,
            customerId = order.customerId,
            providerId = order.providerId,
            paymentId = order.paymentId,
            status = OrderStatus.PLACED,
            fromTime = Instant.now().minusSeconds(3600),
            toTime = Instant.now().plusSeconds(3600),
            page = 0,
            size = 25
        )

        assertEquals(1L, page.totalElements)
        assertEquals(order.orderId, page.content.single().orderId)
    }

    @Test
    fun `order detail uses stored item snapshots and status history`() {
        val order = order()
        val item = OrderItem(
            orderId = order.orderId,
            offeringId = UUID.randomUUID(),
            name = "Pet Food",
            quantity = 2,
            unitPrice = BigDecimal("499.00"),
            lineTotal = BigDecimal("998.00")
        )
        val placed = OrderStatusHistory(
            orderId = order.orderId,
            status = OrderStatus.PLACED,
            changedAt = Instant.parse("2026-08-08T10:00:00Z")
        )
        val accepted = OrderStatusHistory(
            orderId = order.orderId,
            status = OrderStatus.ACCEPTED,
            changedAt = Instant.parse("2026-08-08T10:05:00Z")
        )
        whenever(orderRepository.findById(order.orderId)).thenReturn(Optional.of(order))
        whenever(itemRepository.findByOrderId(order.orderId)).thenReturn(listOf(item))
        whenever(historyRepository.findByOrderId(order.orderId)).thenReturn(listOf(accepted, placed))

        val detail = service.detail(order.orderId)

        assertEquals("Pet Food", detail.items.single().name)
        assertEquals(listOf(OrderStatus.PLACED, OrderStatus.ACCEPTED), detail.timeline.map { it.status })
    }

    @Test
    fun `admin order search rejects invalid date window before database query`() {
        val now = Instant.now()
        assertThrows<IllegalArgumentException> {
            service.search(null, null, null, null, null, now, now.minusSeconds(1), 0, 25)
        }
        verify(orderRepository, never()).searchForAdmin(any(), any(), any(), any(), any(), any(), any(), any())
    }

    private fun order() = Order(
        orderId = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = OrderStatus.PLACED,
        subtotalAmount = BigDecimal("998.00"),
        totalAmount = BigDecimal("998.00"),
        paymentId = UUID.randomUUID()
    )
}