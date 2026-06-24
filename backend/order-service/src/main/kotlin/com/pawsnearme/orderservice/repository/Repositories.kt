package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatusHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByCustomerId(customerId: UUID): List<Order>
    fun findByProviderId(providerId: UUID): List<Order>
}

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, UUID> {
    fun findByOrderId(orderId: UUID): List<OrderItem>
}

@Repository
interface OrderStatusHistoryRepository : JpaRepository<OrderStatusHistory, UUID> {
    fun findByOrderId(orderId: UUID): List<OrderStatusHistory>
}
