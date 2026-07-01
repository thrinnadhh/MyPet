package com.pawsnearme.notificationservice.service

import org.slf4j.LoggerFactory
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
class LoggingPushNotificationDeliveryAdapter : NotificationDeliveryAdapter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun deliver(request: NotificationDeliveryRequest): NotificationDeliveryResult {
        log.info(
            "[EXPO_FCM_PENDING_CONFIG] user={} reference={} template={} message={}",
            request.userId,
            request.referenceId,
            request.templateCode,
            request.message
        )
        return NotificationDeliveryResult(delivered = true, provider = "LOGGED_EXPO_FCM")
    }
}
