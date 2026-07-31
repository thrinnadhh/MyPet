package com.pawsnearme.orderservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_compensations", schema = "orders")
class OrderCompensation(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "compensation_id")
    var compensationId: UUID? = null,

    @Column(name = "order_id")
    var orderId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "coupon_code")
    var couponCode: String? = null,

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    var payloadJson: String,

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0
)
