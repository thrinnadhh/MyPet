package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.RecurringOrderOccurrence
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import com.pawsnearme.orderservice.model.RecurringOrderSubscriptionItem
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface RecurringOrderSubscriptionRepository : JpaRepository<RecurringOrderSubscription, UUID> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<RecurringOrderSubscription>
    fun findByProviderIdOrderByNextOrderAtAsc(providerId: UUID): List<RecurringOrderSubscription>
    fun findByStatusAndNextOrderAtLessThanEqual(status: RecurringOrderStatus, nextOrderAt: Instant): List<RecurringOrderSubscription>
    fun existsByCustomerIdAndSourceOrderIdAndStatusNot(customerId: UUID, sourceOrderId: UUID, status: RecurringOrderStatus): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecurringOrderSubscription s where s.subscriptionId = :subscriptionId")
    fun findByIdForUpdate(@Param("subscriptionId") subscriptionId: UUID): Optional<RecurringOrderSubscription>
}

@Repository
interface RecurringOrderSubscriptionItemRepository : JpaRepository<RecurringOrderSubscriptionItem, UUID> {
    fun findBySubscriptionIdOrderByCreatedAtAsc(subscriptionId: UUID): List<RecurringOrderSubscriptionItem>
}

@Repository
interface RecurringOrderOccurrenceRepository : JpaRepository<RecurringOrderOccurrence, UUID> {
    fun findBySubscriptionIdAndScheduledFor(subscriptionId: UUID, scheduledFor: Instant): RecurringOrderOccurrence?
    fun findBySubscriptionIdOrderByScheduledForDesc(subscriptionId: UUID): List<RecurringOrderOccurrence>
}