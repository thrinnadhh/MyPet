package com.pawsnearme.notificationservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ReminderDeliveryStatus {
    SCHEDULED,
    ATTEMPTED,
    DELIVERED,
    DELIVERED_LOGGED,
    FAILED
}

@Entity
@Table(
    schema = "notifications",
    name = "scheduled_reminders"
)
data class ScheduledReminder(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reminder_id")
    var id: UUID? = null,

    /** The customer/user to notify. */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /** e.g. "APPOINTMENT" */
    @Column(name = "reference_type", nullable = false)
    val referenceType: String,

    /** The appointment / order UUID. */
    @Column(name = "reference_id", nullable = false)
    val referenceId: UUID,

    /** When the reminder should fire (T-24H or T-1H before the appointment). */
    @Column(name = "fire_at", nullable = false)
    val fireAt: Instant,

    @Column(name = "fired", nullable = false)
    var fired: Boolean = false,

    /** Template key, e.g. "APPOINTMENT_T24H", "APPOINTMENT_T1H". */
    @Column(name = "template_code", nullable = false)
    val templateCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, columnDefinition = "reminder_delivery_status")
    var deliveryStatus: ReminderDeliveryStatus = ReminderDeliveryStatus.SCHEDULED,

    @Column(name = "provider")
    var provider: String? = null,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "retryable_failure", nullable = false)
    var retryableFailure: Boolean = false,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
