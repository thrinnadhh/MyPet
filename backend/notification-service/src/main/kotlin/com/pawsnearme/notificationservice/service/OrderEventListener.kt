package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.notificationservice.event.OrderPlacedEvent
import com.pawsnearme.notificationservice.event.OrderStatusChangedEvent
import com.pawsnearme.notificationservice.model.InAppNotification
import com.pawsnearme.notificationservice.repository.InAppNotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderEventListener(
    private val notificationRepo: InAppNotificationRepository,
    private val objectMapper: ObjectMapper,
    private val idempotencyService: IdempotencyService,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
        dltTopicSuffix = ".dlq",
    )
    @KafkaListener(topics = ["orders.events"], groupId = "notification-service-orders")
    @Transactional
    fun onOrderEvent(message: String) {
        val root = runCatching { objectMapper.readTree(message) }.getOrNull() ?: return
        val eventType = root.path("eventType").asText("")
        val eventId = root.path("eventId").asText(null)

        if (eventId != null && !idempotencyService.checkAndRecord(UUID.fromString(eventId))) {
            log.info("Duplicate order notification event ignored: $eventId")
            return
        }

        when (eventType) {
            "OrderPlaced" -> {
                val event = objectMapper.readValue(message, OrderPlacedEvent::class.java)
                notifyMerchant(event.merchantOwnerUserId, event.orderId, event.totalAmount.toPlainString())
            }
            "OrderStatusChanged" -> {
                val event = objectMapper.readValue(message, OrderStatusChangedEvent::class.java)
                if (event.toStatus == "ACCEPTED") {
                    notifyMerchant(event.merchantOwnerUserId, event.orderId, event.totalAmount.toPlainString())
                }
            }
        }
    }

    private fun notifyMerchant(merchantUserId: UUID?, orderId: UUID, amount: String) {
        if (merchantUserId == null) {
            log.warn("Skipping merchant alert for order {} — merchantOwnerUserId missing", orderId)
            return
        }
        notificationRepo.save(
            InAppNotification(
                userId = merchantUserId,
                notificationType = "MERCHANT_ORDER_ALERT",
                title = "New order received",
                body = "Order #${orderId.toString().take(8)} · ₹$amount — pack before pickup",
                referenceId = orderId,
                priority = "HIGH",
            )
        )
        pushNotificationService.sendMerchantOrderAlert(merchantUserId, orderId, amount)
        log.info("Created merchant order alert for user {} order {}", merchantUserId, orderId)
    }
}
