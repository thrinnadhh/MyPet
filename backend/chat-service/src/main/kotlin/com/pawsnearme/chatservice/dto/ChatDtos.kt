package com.pawsnearme.chatservice.dto

import com.pawsnearme.chatservice.model.ChatContextType
import com.pawsnearme.chatservice.model.ChatMessageType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateConversationRequest(
    @field:NotNull val contextType: ChatContextType,
    @field:NotNull val contextId: UUID,
    @field:NotNull val providerId: UUID,
    val customerId: UUID? = null,
    val assignedDoctorUserId: UUID? = null
)

data class UpdateConversationPrivacyRequest(
    val customerPhoneVisible: Boolean? = null,
    val doctorPhoneVisible: Boolean? = null,
    val assignedDoctorUserId: UUID? = null
)

data class SendMessageRequest(
    @field:NotNull val messageType: ChatMessageType,
    val body: String? = null,
    val imageUrl: String? = null,
    val imageMimeType: String? = null
)

data class ParticipantContactDto(
    val userId: UUID,
    val displayName: String,
    val phoneNumber: String?,
    val phoneHidden: Boolean
)

data class ConversationPrivacyDto(
    val customerPhoneVisible: Boolean,
    val doctorPhoneVisible: Boolean,
    val assignedDoctorUserId: UUID?,
    val canManagePrivacy: Boolean
)

data class ConversationDto(
    val conversationId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val providerName: String,
    val providerType: String,
    val contextType: ChatContextType,
    val contextId: UUID,
    val customer: ParticipantContactDto,
    val merchant: ParticipantContactDto,
    val doctor: ParticipantContactDto?,
    val privacy: ConversationPrivacyDto,
    val lastMessagePreview: String?,
    val lastMessageAt: Instant?,
    val unreadCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class MessageDto(
    val messageId: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val senderRole: String,
    val senderName: String,
    val messageType: ChatMessageType,
    val body: String?,
    val imageUrl: String?,
    val imageMimeType: String?,
    val sentAt: Instant,
    val readAt: Instant?
)

data class AttachmentUploadResponse(
    val imageUrl: String,
    val imageMimeType: String
)
