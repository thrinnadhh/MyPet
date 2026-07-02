package com.pawsnearme.notificationservice.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

data class NotificationDeliveryRequest(
    val userId: UUID,
    val referenceId: UUID,
    val referenceType: String,
    val templateCode: String,
    val message: String
)

data class NotificationDeliveryResult(
    val delivered: Boolean,
    val provider: String,
    val retryable: Boolean = false,
    val failureReason: String? = null
)

interface NotificationDeliveryAdapter {
    fun deliver(request: NotificationDeliveryRequest): NotificationDeliveryResult
}

@Component
class ConfiguredNotificationDeliveryAdapter(
    @Value("\${notification.delivery.mode:LOGGED_DEV}")
    private val deliveryMode: String
) : NotificationDeliveryAdapter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun deliver(request: NotificationDeliveryRequest): NotificationDeliveryResult {
        if (deliveryMode.equals("EXPO_FCM", ignoreCase = true)) {
            return NotificationDeliveryResult(
                delivered = false,
                provider = "EXPO_FCM",
                retryable = false,
                failureReason = "Expo/FCM push token registration is not configured for this user."
            )
        }

        if (!deliveryMode.equals("LOGGED_DEV", ignoreCase = true)) {
            return NotificationDeliveryResult(
                delivered = false,
                provider = deliveryMode,
                retryable = false,
                failureReason = "Unsupported notification delivery mode: $deliveryMode"
            )
        }

        log.info(
            "[LOGGED_DEV_REMINDER] user={} reference={} template={} message={}",
            request.userId,
            request.referenceId,
            request.templateCode,
            request.message
        )
        return NotificationDeliveryResult(delivered = true, provider = "LOGGED_DEV")
    }
}
