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
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByCustomerId(customerId: UUID): List<Order>
    fun findByProviderId(providerId: UUID): List<Order>
    fun findByRecurringOccurrenceId(recurringOccurrenceId: UUID): Optional<Order>
    fun findByStatusAndDeliveredAtBefore(status: OrderStatus, deliveredBefore: Instant): List<Order>
    fun countByStatusIn(statuses: Collection<OrderStatus>): Long
    fun countByStatusInAndPlacedAtBefore(statuses: Collection<OrderStatus>, placedAt: Instant): Long
    fun countByPaymentStatusIgnoreCase(paymentStatus: String): Long

    @Query(
        """
        select o from Order o
        where (:orderId is null or o.orderId = :orderId)
          and (:customerId is null or o.customerId = :customerId)
          and (:providerId is null or o.providerId = :providerId)
          and (:paymentId is null or o.paymentId = :paymentId)
          and (:status is null or o.status = :status)
          and (:fromTime is null or o.placedAt >= :fromTime)
          and (:toTime is null or o.placedAt < :toTime)
        order by o.placedAt desc
        """
    )
    fun searchForAdmin(
        @Param("orderId") orderId: UUID?,
        @Param("customerId") customerId: UUID?,
        @Param("providerId") providerId: UUID?,
        @Param("paymentId") paymentId: UUID?,
        @Param("status") status: OrderStatus?,
        @Param("fromTime") fromTime: Instant?,
        @Param("toTime") toTime: Instant?,
        pageable: Pageable
    ): Page<Order>

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
    fun countByStatusIgnoreCase(status: String): Long
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Dispute>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.disputeId = :disputeId")
    fun findByIdForUpdate(@Param("disputeId") disputeId: UUID): Optional<Dispute>
}

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    fun findByOrderId(orderId: UUID): Optional<Invoice>
}

@Repository
interface SupportCaseRepository : JpaRepository<SupportCase, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<SupportCase>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<SupportCase>
    fun countByStatusIgnoreCase(status: String): Long
}