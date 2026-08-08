package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.OrderStatusHistory
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AdminOrderPage(
    val content: List<Order>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class AdminOrderDetail(
    val order: Order,
    val items: List<OrderItem>,
    val timeline: List<OrderStatusHistory>
)

@Service
class AdminOrderQueryService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val historyRepository: OrderStatusHistoryRepository
) {
    @Transactional(readOnly = true)
    fun search(
        orderId: UUID?,
        customerId: UUID?,
        providerId: UUID?,
        paymentId: UUID?,
        status: OrderStatus?,
        fromTime: Instant?,
        toTime: Instant?,
        page: Int,
        size: Int
    ): AdminOrderPage {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        if (fromTime != null && toTime != null) require(fromTime < toTime) { "fromTime must be before toTime" }
        val result = orderRepository.searchForAdmin(
            orderId,
            customerId,
            providerId,
            paymentId,
            status,
            fromTime,
            toTime,
            PageRequest.of(page, size)
        )
        return AdminOrderPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun detail(orderId: UUID): AdminOrderDetail {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order not found: $orderId") }
        return AdminOrderDetail(
            order = order,
            items = orderItemRepository.findByOrderId(orderId),
            timeline = historyRepository.findByOrderId(orderId).sortedBy { it.changedAt }
        )
    }
}