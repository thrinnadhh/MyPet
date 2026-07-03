package com.pawsnearme.chatservice.service

import com.pawsnearme.chatservice.dto.*
import com.pawsnearme.chatservice.model.*
import com.pawsnearme.chatservice.repository.*
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val profileRefRepository: ProfileRefRepository,
    private val providerRefRepository: ProviderRefRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createOrGetConversation(
        request: CreateConversationRequest,
        callerId: UUID,
        callerRole: String
    ): ConversationDto {
        val normalizedRole = normalizeRole(callerRole)
        val provider = providerRefRepository.findById(request.providerId)
            .orElseThrow { NoSuchElementException("Provider ${request.providerId} not found") }

        val customerId = when (normalizedRole) {
            "CUSTOMER" -> callerId
            "MERCHANT", "ADMIN" -> {
                val customerId = request.customerId
                    ?: throw IllegalArgumentException("customerId is required when creating a conversation as merchant.")
                customerId
            }
            else -> throw IllegalArgumentException("Role $callerRole cannot create conversations.")
        }

        if (normalizedRole == "MERCHANT" && provider.ownerUserId != callerId) {
            throw IllegalArgumentException("Merchant does not own provider ${request.providerId}.")
        }

        val existing = conversationRepository.findByContextTypeAndContextId(request.contextType, request.contextId)
        if (existing != null) {
            assertConversationAccess(existing, callerId, normalizedRole)
            return toConversationDto(existing, callerId, normalizedRole)
        }

        val conversation = conversationRepository.save(
            Conversation(
                customerId = customerId,
                providerId = request.providerId,
                contextType = request.contextType,
                contextId = request.contextId,
                providerType = provider.providerType,
                assignedDoctorUserId = request.assignedDoctorUserId
            )
        )
        return toConversationDto(conversation, callerId, normalizedRole)
    }

    fun listConversations(callerId: UUID, callerRole: String): List<ConversationDto> {
        val normalizedRole = normalizeRole(callerRole)
        val conversations = when (normalizedRole) {
            "CUSTOMER" -> conversationRepository.findByCustomerIdOrderByUpdatedAtDesc(callerId)
            "MERCHANT", "ADMIN" -> {
                val ownedProviderIds = providerRefRepository.findByOwnerUserId(callerId).map { it.providerId }
                if (ownedProviderIds.isEmpty()) emptyList()
                else ownedProviderIds.flatMap { providerId ->
                    conversationRepository.findByProviderIdOrderByUpdatedAtDesc(providerId)
                }.sortedByDescending { it.updatedAt }
            }
            else -> throw IllegalArgumentException("Role $callerRole cannot list conversations.")
        }
        return conversations.map { toConversationDto(it, callerId, normalizedRole) }
    }

    fun getConversation(conversationId: UUID, callerId: UUID, callerRole: String): ConversationDto {
        val conversation = loadConversation(conversationId)
        assertConversationAccess(conversation, callerId, normalizeRole(callerRole))
        return toConversationDto(conversation, callerId, normalizeRole(callerRole))
    }

    fun listMessages(
        conversationId: UUID,
        callerId: UUID,
        callerRole: String,
        after: Instant?
    ): List<MessageDto> {
        val conversation = loadConversation(conversationId)
        val normalizedRole = normalizeRole(callerRole)
        assertConversationAccess(conversation, callerId, normalizedRole)

        val messages = if (after != null) {
            messageRepository.findByConversationIdAndSentAtAfterOrderBySentAtAsc(conversationId, after)
        } else {
            messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)
        }

        return messages.map { toMessageDto(it, conversation) }
    }

    @Transactional
    fun sendMessage(
        conversationId: UUID,
        request: SendMessageRequest,
        callerId: UUID,
        callerRole: String
    ): MessageDto {
        validateMessageRequest(request)
        val conversation = loadConversation(conversationId)
        val normalizedRole = normalizeRole(callerRole)
        assertConversationAccess(conversation, callerId, normalizedRole)

        val senderRole = when (normalizedRole) {
            "CUSTOMER" -> ChatSenderRole.CUSTOMER
            "MERCHANT", "ADMIN" -> ChatSenderRole.MERCHANT
            else -> throw IllegalArgumentException("Role $callerRole cannot send messages.")
        }

        if (senderRole == ChatSenderRole.CUSTOMER && conversation.customerId != callerId) {
            throw IllegalArgumentException("Customer cannot send messages in another customer's conversation.")
        }
        if (senderRole == ChatSenderRole.MERCHANT) {
            val provider = providerRefRepository.findById(conversation.providerId)
                .orElseThrow { NoSuchElementException("Provider not found") }
            if (provider.ownerUserId != callerId && normalizedRole != "ADMIN") {
                throw IllegalArgumentException("Merchant does not own this conversation.")
            }
        }

        val saved = messageRepository.save(
            Message(
                conversationId = conversationId,
                senderId = callerId,
                senderRole = senderRole,
                messageType = request.messageType,
                body = request.body?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = request.imageUrl,
                imageMimeType = request.imageMimeType
            )
        )

        conversation.updatedAt = Instant.now()
        conversationRepository.save(conversation)

        publishMessageEvent(conversation, saved)

        return toMessageDto(saved, conversation)
    }

    @Transactional
    fun updatePrivacy(
        conversationId: UUID,
        request: UpdateConversationPrivacyRequest,
        callerId: UUID,
        callerRole: String
    ): ConversationDto {
        val conversation = loadConversation(conversationId)
        val normalizedRole = normalizeRole(callerRole)
        if (normalizedRole !in setOf("MERCHANT", "ADMIN")) {
            throw IllegalArgumentException("Only merchants can update conversation privacy.")
        }

        val provider = providerRefRepository.findById(conversation.providerId)
            .orElseThrow { NoSuchElementException("Provider not found") }
        if (provider.ownerUserId != callerId && normalizedRole != "ADMIN") {
            throw IllegalArgumentException("Merchant does not own this conversation.")
        }

        request.customerPhoneVisible?.let { conversation.customerPhoneVisible = it }
        request.doctorPhoneVisible?.let {
            if (it && !conversation.isVetProvider) {
                throw IllegalArgumentException("Doctor phone visibility applies only to vet conversations.")
            }
            conversation.doctorPhoneVisible = it
        }
        request.assignedDoctorUserId?.let { doctorId ->
            if (!conversation.isVetProvider) {
                throw IllegalArgumentException("Assigned doctor applies only to vet conversations.")
            }
            profileRefRepository.findById(doctorId)
                .orElseThrow { NoSuchElementException("Doctor profile $doctorId not found") }
            conversation.assignedDoctorUserId = doctorId
        }

        conversation.updatedAt = Instant.now()
        val saved = conversationRepository.save(conversation)
        return toConversationDto(saved, callerId, normalizedRole)
    }

    @Transactional
    fun markAsRead(conversationId: UUID, callerId: UUID, callerRole: String) {
        val conversation = loadConversation(conversationId)
        assertConversationAccess(conversation, callerId, normalizeRole(callerRole))
        messageRepository.markUnreadAsRead(conversationId, callerId, Instant.now())
    }

    private fun validateMessageRequest(request: SendMessageRequest) {
        when (request.messageType) {
            ChatMessageType.TEXT -> {
                if (request.body.isNullOrBlank()) {
                    throw IllegalArgumentException("Text messages require body.")
                }
            }
            ChatMessageType.IMAGE -> {
                if (request.imageUrl.isNullOrBlank()) {
                    throw IllegalArgumentException("Image messages require imageUrl.")
                }
                val mime = request.imageMimeType?.lowercase()
                if (mime.isNullOrBlank() || mime !in ALLOWED_IMAGE_MIME_TYPES) {
                    throw IllegalArgumentException("Unsupported image mime type.")
                }
            }
        }
    }

    private fun publishMessageEvent(conversation: Conversation, message: Message) {
        val recipientUserId = if (message.senderRole == ChatSenderRole.CUSTOMER) {
            providerRefRepository.findById(conversation.providerId).map { it.ownerUserId }.orElse(null)
        } else {
            conversation.customerId
        }

        if (recipientUserId == null) return

        val preview = when (message.messageType) {
            ChatMessageType.IMAGE -> "Sent an image"
            ChatMessageType.TEXT -> message.body?.take(120) ?: "New message"
        }

        val event = mapOf(
            "event_id" to UUID.randomUUID().toString(),
            "event_type" to "ChatMessageSent",
            "occurred_at" to Instant.now().toString(),
            "conversation_id" to conversation.conversationId.toString(),
            "message_id" to message.messageId.toString(),
            "recipient_user_id" to recipientUserId.toString(),
            "sender_role" to message.senderRole.name,
            "message_preview" to preview,
            "message_type" to message.messageType.name,
            "context_type" to conversation.contextType.name,
            "context_id" to conversation.contextId.toString()
        )

        runCatching {
            kafkaTemplate.send("chat.events", conversation.conversationId.toString(), event)
        }.onFailure { ex ->
            log.warn("Failed to publish chat event for message {}: {}", message.messageId, ex.message)
        }
    }

    private fun loadConversation(conversationId: UUID): Conversation =
        conversationRepository.findById(conversationId)
            .orElseThrow { NoSuchElementException("Conversation $conversationId not found") }

    private fun assertConversationAccess(conversation: Conversation, callerId: UUID, callerRole: String) {
        when (callerRole) {
            "CUSTOMER" -> {
                if (conversation.customerId != callerId) {
                    throw IllegalArgumentException("Customer cannot access this conversation.")
                }
            }
            "MERCHANT", "ADMIN" -> {
                val provider = providerRefRepository.findById(conversation.providerId).orElse(null)
                if (provider == null || (provider.ownerUserId != callerId && callerRole != "ADMIN")) {
                    throw IllegalArgumentException("Merchant cannot access this conversation.")
                }
            }
            else -> throw IllegalArgumentException("Role $callerRole cannot access conversations.")
        }
    }

    private fun toConversationDto(
        conversation: Conversation,
        viewerId: UUID,
        viewerRole: String
    ): ConversationDto {
        val provider = providerRefRepository.findById(conversation.providerId).orElse(null)
        val customerProfile = profileRefRepository.findById(conversation.customerId).orElse(null)
        val merchantProfile = provider?.let { profileRefRepository.findById(it.ownerUserId).orElse(null) }
        val doctorProfile = conversation.assignedDoctorUserId?.let {
            profileRefRepository.findById(it).orElse(null)
        }

        val messages = messageRepository.findByConversationIdOrderBySentAtAsc(conversation.conversationId!!)
        val lastMessage = messages.lastOrNull()
        val unreadCount = messages.count { it.senderId != viewerId && it.readAt == null }

        val canManagePrivacy = viewerRole in setOf("MERCHANT", "ADMIN")

        return ConversationDto(
            conversationId = conversation.conversationId!!,
            customerId = conversation.customerId,
            providerId = conversation.providerId,
            providerName = provider?.name ?: "Provider",
            providerType = conversation.providerType,
            contextType = conversation.contextType,
            contextId = conversation.contextId,
            customer = buildParticipant(
                userId = conversation.customerId,
                profile = customerProfile,
                phoneVisible = canManagePrivacy && conversation.customerPhoneVisible,
                defaultName = "Customer"
            ),
            merchant = buildParticipant(
                userId = provider?.ownerUserId ?: conversation.providerId,
                profile = merchantProfile,
                phoneVisible = viewerRole == "CUSTOMER",
                defaultName = provider?.name ?: "Merchant"
            ),
            doctor = if (conversation.isVetProvider) {
                buildParticipant(
                    userId = conversation.assignedDoctorUserId ?: provider?.ownerUserId ?: conversation.providerId,
                    profile = doctorProfile ?: merchantProfile,
                    phoneVisible = viewerRole == "CUSTOMER" && conversation.doctorPhoneVisible,
                    defaultName = doctorProfile?.fullName?.let { "Dr. $it" } ?: "Doctor"
                )
            } else {
                null
            },
            privacy = ConversationPrivacyDto(
                customerPhoneVisible = conversation.customerPhoneVisible,
                doctorPhoneVisible = conversation.doctorPhoneVisible,
                assignedDoctorUserId = conversation.assignedDoctorUserId,
                canManagePrivacy = canManagePrivacy
            ),
            lastMessagePreview = lastMessage?.let {
                when (it.messageType) {
                    ChatMessageType.IMAGE -> "Image"
                    ChatMessageType.TEXT -> it.body
                }
            },
            lastMessageAt = lastMessage?.sentAt,
            unreadCount = unreadCount,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt
        )
    }

    private fun buildParticipant(
        userId: UUID,
        profile: ProfileRef?,
        phoneVisible: Boolean,
        defaultName: String
    ): ParticipantContactDto {
        val phone = profile?.phoneNumber
        return ParticipantContactDto(
            userId = userId,
            displayName = profile?.fullName ?: defaultName,
            phoneNumber = if (phoneVisible) phone else null,
            phoneHidden = !phoneVisible && !phone.isNullOrBlank()
        )
    }

    private fun toMessageDto(message: Message, conversation: Conversation): MessageDto {
        val senderName = when (message.senderRole) {
            ChatSenderRole.CUSTOMER -> profileRefRepository.findById(conversation.customerId)
                .map { it.fullName }.orElse("Customer")
            ChatSenderRole.MERCHANT -> providerRefRepository.findById(conversation.providerId)
                .map { it.name }.orElse("Merchant")
        }

        return MessageDto(
            messageId = message.messageId!!,
            conversationId = message.conversationId,
            senderId = message.senderId,
            senderRole = message.senderRole.name,
            senderName = senderName,
            messageType = message.messageType,
            body = message.body,
            imageUrl = message.imageUrl,
            imageMimeType = message.imageMimeType,
            sentAt = message.sentAt,
            readAt = message.readAt
        )
    }

    private fun normalizeRole(role: String): String {
        val normalized = role.uppercase()
        return if (normalized == "PROVIDER") "MERCHANT" else normalized
    }

    companion object {
        private val ALLOWED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
