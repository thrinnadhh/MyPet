package com.pawsnearme.notificationservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "in_app_notifications", schema = "notifications")
class InAppNotification(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "notification_id")
    var notificationId: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "notification_type", nullable = false)
    var notificationType: String,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "body", nullable = false)
    var body: String,

    @Column(name = "reference_id")
    var referenceId: UUID? = null,

    @Column(name = "priority", nullable = false)
    var priority: String = "NORMAL",

    @Column(name = "read_at")
    var readAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
