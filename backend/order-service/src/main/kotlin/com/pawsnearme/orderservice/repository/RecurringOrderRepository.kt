package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RecurringOrderSubscriptionRepository : JpaRepository<RecurringOrderSubscription, UUID> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<RecurringOrderSubscription>
    fun findByStatusAndNextOrderAtLessThanEqual(status: RecurringOrderStatus, nextOrderAt: Instant): List<RecurringOrderSubscription>
    fun existsByCustomerIdAndSourceOrderIdAndStatusNot(customerId: UUID, sourceOrderId: UUID, status: RecurringOrderStatus): Boolean
}
