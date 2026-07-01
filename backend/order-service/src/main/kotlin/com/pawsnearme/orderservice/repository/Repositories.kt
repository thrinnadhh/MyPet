package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatusHistory
import com.pawsnearme.orderservice.model.SystemConfig
import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Invoice
import com.pawsnearme.orderservice.model.SupportCase
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

@Repository
interface SystemConfigRepository : JpaRepository<SystemConfig, String>

@Repository
interface DisputeRepository : JpaRepository<Dispute, UUID> {
    fun findByOrderId(orderId: UUID): List<Dispute>
}

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    fun findByOrderId(orderId: UUID): java.util.Optional<Invoice>
}

@Repository
interface SupportCaseRepository : JpaRepository<SupportCase, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<SupportCase>
}
