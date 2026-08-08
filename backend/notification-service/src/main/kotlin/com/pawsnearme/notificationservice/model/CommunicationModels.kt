package com.pawsnearme.notificationservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification_contacts", schema = "notifications")
class NotificationContact(
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "email", length = 320)
    var email: String? = null,

    @Column(name = "display_name", length = 200)
    var displayName: String? = null,

    @Column(name = "phone", length = 32)
    var phone: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

data class NotificationReferenceOwnerId(
    var referenceType: String = "",
    var referenceId: UUID = UUID(0, 0),
) : Serializable

@Entity
@IdClass(NotificationReferenceOwnerId::class)
@Table(name = "notification_reference_owners", schema = "notifications")
class NotificationReferenceOwner(
    @Id
    @Column(name = "reference_type", nullable = false, length = 40)
    var referenceType: String,

    @Id
    @Column(name = "reference_id", nullable = false)
    var referenceId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "email_deliveries", schema = "notifications")
class EmailDelivery(
    @Id
    @Column(name = "email_delivery_id", nullable = false)
    var emailDeliveryId: UUID = UUID.randomUUID(),

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180)
    var idempotencyKey: String,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "recipient_email", nullable = false, length = 320)
    var recipientEmail: String,

    @Column(name = "recipient_name", length = 200)
    var recipientName: String? = null,

    @Column(name = "template_code", nullable = false, length = 80)
    var templateCode: String,

    @Column(name = "variables_json", nullable = false, columnDefinition = "text")
    var variablesJson: String,

    @Column(name = "provider", length = 30)
    var provider: String? = null,

    @Column(name = "provider_message_id", length = 255)
    var providerMessageId: String? = null,

    @Column(name = "status", nullable = false, length = 24)
    var status: String = "PENDING",

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "next_attempt_at")
    var nextAttemptAt: Instant? = null,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "sent_at")
    var sentAt: Instant? = null,
)
