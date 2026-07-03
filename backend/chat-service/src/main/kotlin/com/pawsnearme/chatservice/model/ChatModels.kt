package com.pawsnearme.chatservice.model

import jakarta.persistence.*
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

enum class ChatContextType {
    ORDER, APPOINTMENT
}

enum class ChatMessageType {
    TEXT, IMAGE
}

enum class ChatSenderRole {
    CUSTOMER, MERCHANT
}

@Entity
@Table(name = "conversations", schema = "chat")
class Conversation(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "conversation_id")
    var conversationId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false)
    var contextType: ChatContextType,

    @Column(name = "context_id", nullable = false)
    var contextId: UUID,

    @Column(name = "provider_type", nullable = false)
    var providerType: String = "PET_STORE",

    @Column(name = "customer_phone_visible", nullable = false)
    var customerPhoneVisible: Boolean = false,

    @Column(name = "doctor_phone_visible", nullable = false)
    var doctorPhoneVisible: Boolean = false,

    @Column(name = "assigned_doctor_user_id")
    var assignedDoctorUserId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }

    val isVetProvider: Boolean
        get() = providerType.equals("VET_HOSPITAL", ignoreCase = true)
}

@Entity
@Table(name = "messages", schema = "chat")
class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "message_id")
    var messageId: UUID? = null,

    @Column(name = "conversation_id", nullable = false)
    var conversationId: UUID,

    @Column(name = "sender_id", nullable = false)
    var senderId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    var senderRole: ChatSenderRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    var messageType: ChatMessageType,

    @Column(name = "body")
    var body: String? = null,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(name = "image_mime_type")
    var imageMimeType: String? = null,

    @Column(name = "sent_at", nullable = false, updatable = false)
    var sentAt: Instant = Instant.now(),

    @Column(name = "read_at")
    var readAt: Instant? = null
)

@Entity
@Immutable
@Table(name = "profiles", schema = "identity")
class ProfileRef(
    @Id
    @Column(name = "user_id")
    var userId: UUID,

    @Column(name = "full_name", nullable = false)
    var fullName: String,

    @Column(name = "phone_number", nullable = false)
    var phoneNumber: String
)

@Entity
@Immutable
@Table(name = "providers", schema = "providers")
class ProviderRef(
    @Id
    @Column(name = "provider_id")
    var providerId: UUID,

    @Column(name = "owner_user_id", nullable = false)
    var ownerUserId: UUID,

    @Column(name = "provider_type", nullable = false)
    var providerType: String,

    @Column(name = "name", nullable = false)
    var name: String
)
