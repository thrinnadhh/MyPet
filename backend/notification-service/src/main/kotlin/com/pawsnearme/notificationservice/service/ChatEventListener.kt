package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.notificationservice.event.ChatMessageEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service

@Service
class ChatEventListener(
    private val objectMapper: ObjectMapper,
    private val idempotencyService: IdempotencyService,
    private val deliveryAdapter: NotificationDeliveryAdapter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
        dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = ["chat.events"], groupId = "notification-service-chat")
    fun onChatEvent(message: String) {
        val event = runCatching {
            objectMapper.readValue(message, ChatMessageEvent::class.java)
        }.getOrNull() ?: return log.warn("Could not parse chat event: $message")

        if (event.eventType != "ChatMessageSent") return
        if (!idempotencyService.checkAndRecord(event.eventId)) {
            log.info("NotificationService: Duplicate chat event ignored: {}", event.eventId)
            return
        }

        val preview = if (event.messageType == "IMAGE") {
            "New image in your chat"
        } else {
            event.messagePreview
        }

        val result = deliveryAdapter.deliver(
            NotificationDeliveryRequest(
                userId = event.recipientUserId,
                referenceId = event.conversationId,
                referenceType = "CHAT",
                templateCode = "CHAT_MESSAGE",
                message = preview
            )
        )

        if (result.delivered) {
            log.info("Delivered chat notification for conversation {} to {}", event.conversationId, event.recipientUserId)
        } else {
            log.warn(
                "Chat notification not delivered for conversation {} reason={}",
                event.conversationId,
                result.failureReason
            )
        }
    }
}
