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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RecurringOrderStatus = RecurringOrderStatus.ACTIVE,

    @Column(name = "next_order_at", nullable = false)
    var nextOrderAt: Instant,

    @Column(name = "last_reminded_at")
    var lastRemindedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
