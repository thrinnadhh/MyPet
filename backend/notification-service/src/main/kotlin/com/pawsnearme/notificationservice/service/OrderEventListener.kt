package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.notificationservice.event.MerchantOrderActionableEvent
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
    private val transactionalEmailService: TransactionalEmailService,
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
                transactionalEmailService.registerReferenceOwner("ORDER", event.orderId, event.customerId)
                transactionalEmailService.enqueueForUser(
                    userId = event.customerId,
                    templateCode = "ORDER_PLACED",
                    idempotencyKey = "order:${event.orderId}:placed",
                    variables = mapOf(
                        "order_id" to event.orderId.toString(),
                        "order_short_id" to event.orderId.toString().take(8),
                        "total_amount" to event.totalAmount.toPlainString(),
                        "provider_id" to event.providerId.toString(),
                    ),
                )
            }
            "MerchantOrderActionable" -> {
                val event = objectMapper.readValue(message, MerchantOrderActionableEvent::class.java)
                notifyMerchant(event.merchantOwnerUserId, event.orderId, event.totalAmount.toPlainString())
            }
            "OrderStatusChanged", "OrderCancelled" -> {
                val event = objectMapper.readValue(message, OrderStatusChangedEvent::class.java)
                notifyCustomerStatus(event)
                if (event.toStatus == "DELIVERED") {
                    transactionalEmailService.enqueueForReference(
                        referenceType = "ORDER",
                        referenceId = event.orderId,
                        templateCode = "ORDER_DELIVERED",
                        idempotencyKey = "order:${event.orderId}:delivered",
                        variables = mapOf(
                            "order_id" to event.orderId.toString(),
                            "order_short_id" to event.orderId.toString().take(8),
                            "total_amount" to event.totalAmount.toPlainString(),
                            "delivery_fee" to event.deliveryFee.toPlainString(),
                        ),
                    )
                }
            }
        }
    }

    private fun notifyCustomerStatus(event: OrderStatusChangedEvent) {
        val copy = when (event.toStatus) {
            "ACCEPTED" -> "Order accepted" to "The merchant accepted order #${event.orderId.toString().take(8)}."
            "REJECTED" -> "Order rejected" to "The merchant could not accept order #${event.orderId.toString().take(8)}."
            "PREPARING" -> "Order being prepared" to "The merchant started preparing order #${event.orderId.toString().take(8)}."
            "ASSIGNED" -> "Delivery captain assigned" to "A captain has been assigned to order #${event.orderId.toString().take(8)}."
            "PICKED_UP" -> "Order picked up" to "Your order #${event.orderId.toString().take(8)} is on the way."
            "DELIVERED" -> "Order delivered" to "Order #${event.orderId.toString().take(8)} has been delivered."
            "CANCELLED" -> "Order cancelled" to "Order #${event.orderId.toString().take(8)} has been cancelled."
            else -> return
        }

        notificationRepo.save(
            InAppNotification(
                userId = event.customerId,
                notificationType = "ORDER_STATUS_UPDATE",
                title = copy.first,
                body = copy.second,
                referenceId = event.orderId,
                priority = if (event.toStatus in setOf("REJECTED", "CANCELLED", "DELIVERED")) "HIGH" else "NORMAL",
            )
        )
        pushNotificationService.sendToUser(
            userId = event.customerId,
            title = copy.first,
            body = copy.second,
            templateCode = "ORDER_STATUS_UPDATE",
            referenceId = event.orderId,
        )
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
                body = "Order #${orderId.toString().take(8)} · ₹$amount — review and accept or reject",
                referenceId = orderId,
                priority = "HIGH",
            )
        )
        pushNotificationService.sendMerchantOrderAlert(merchantUserId, orderId, amount)
        log.info("Created merchant order alert for user {} order {}", merchantUserId, orderId)
    }
}
