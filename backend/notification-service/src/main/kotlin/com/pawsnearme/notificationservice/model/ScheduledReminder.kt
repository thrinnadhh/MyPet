package com.pawsnearme.notificationservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    schema = "notifications",
    name = "scheduled_reminders"
)
data class ScheduledReminder(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reminder_id")
    val id: UUID = UUID.randomUUID(),

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
    val templateCode: String
)
