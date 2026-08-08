package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Invoice
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.OrderStatusHistory
import com.pawsnearme.orderservice.model.SupportCase
import com.pawsnearme.orderservice.model.SystemConfig
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByCustomerId(customerId: UUID): List<Order>
    fun findByProviderId(providerId: UUID): List<Order>
    fun findByRecurringOccurrenceId(recurringOccurrenceId: UUID): Optional<Order>
    fun findByStatusAndDeliveredAtBefore(status: OrderStatus, deliveredBefore: java.time.Instant): List<Order>

    /**
     * All order lifecycle mutations must serialize through this row lock so that
     * competing customer, merchant, payment and dispatch actions cannot both
     * commit from the same stale state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId")
    fun findByIdForUpdate(@Param("orderId") orderId: UUID): Optional<Order>
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
    fun findByOrderId(orderId: UUID): Optional<Invoice>
}

@Repository
interface SupportCaseRepository : JpaRepository<SupportCase, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<SupportCase>
}