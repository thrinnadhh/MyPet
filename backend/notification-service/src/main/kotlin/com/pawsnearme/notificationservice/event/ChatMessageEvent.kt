package com.pawsnearme.notificationservice.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ChatMessageEvent(
    val eventId: UUID,
    val eventType: String,
    val conversationId: UUID,
    val messageId: UUID,
    val recipientUserId: UUID,
    val senderRole: String,
    val messagePreview: String,
    val messageType: String,
    val contextType: String,
    val contextId: UUID
)
