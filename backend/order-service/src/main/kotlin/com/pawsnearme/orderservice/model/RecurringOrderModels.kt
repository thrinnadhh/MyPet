package com.pawsnearme.orderservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class RecurringOrderStatus {
    ACTIVE,
    PAUSED,
    AWAITING_CONFIRMATION,
    CANCELLED
}

enum class RecurringOrderOccurrenceStatus {
    PROCESSING,
    ORDER_CREATED,
    FAILED
}

@Entity
@Table(name = "recurring_order_subscriptions", schema = "orders")
class RecurringOrderSubscription(
    @Id
    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: UUID = UUID.randomUUID(),

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "source_order_id", nullable = false)
    var sourceOrderId: UUID,

    @Column(name = "delivery_address_id", nullable = false)
    var deliveryAddressId: UUID,

    @Column(name = "cadence_days", nullable = false)
    var cadenceDays: Int,

    @Column(name = "quantity_multiplier", nullable = false)
    var quantityMultiplier: Int = 1,

    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String = "COD",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RecurringOrderStatus = RecurringOrderStatus.ACTIVE,

    @Column(name = "next_order_at", nullable = false)
    var nextOrderAt: Instant,

    @Column(name = "last_reminded_at")
    var lastRemindedAt: Instant? = null,

    @Column(name = "last_executed_at")
    var lastExecutedAt: Instant? = null,

    @Column(name = "last_order_id")
    var lastOrderId: UUID? = null,

    @Column(name = "last_failure_code")
    var lastFailureCode: String? = null,

    @Column(name = "last_failure_detail")
    var lastFailureDetail: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "recurring_order_occurrences", schema = "orders")
class RecurringOrderOccurrence(
    @Id
    @Column(name = "occurrence_id", nullable = false)
    var occurrenceId: UUID = UUID.randomUUID(),

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: UUID,

    @Column(name = "scheduled_for", nullable = false)
    var scheduledFor: Instant,

    @Column(name = "order_id")
    var orderId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RecurringOrderOccurrenceStatus = RecurringOrderOccurrenceStatus.PROCESSING,

    @Column(name = "failure_code")
    var failureCode: String? = null,

    @Column(name = "failure_detail")
    var failureDetail: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)