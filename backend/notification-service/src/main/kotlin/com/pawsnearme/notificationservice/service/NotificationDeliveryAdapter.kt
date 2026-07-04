package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.repository.DevicePushTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

data class NotificationDeliveryRequest(
    val userId: UUID,
    val referenceId: UUID,
    val referenceType: String,
    val templateCode: String,
    val message: String,
    val title: String? = null,
)

data class NotificationDeliveryResult(
    val delivered: Boolean,
    val provider: String,
    val retryable: Boolean = false,
    val failureReason: String? = null,
)

interface NotificationDeliveryAdapter {
    fun deliver(request: NotificationDeliveryRequest): NotificationDeliveryResult
}

@Component
class ConfiguredNotificationDeliveryAdapter(
    @Value("\${notification.delivery.mode:LOGGED_DEV}")
    private val deliveryMode: String,
    private val pushTokenRepository: DevicePushTokenRepository,
    private val pushNotificationService: PushNotificationService,
) : NotificationDeliveryAdapter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun deliver(request: NotificationDeliveryRequest): NotificationDeliveryResult {
        val tokens = pushTokenRepository.findByUserId(request.userId)
        if (tokens.isNotEmpty()) {
            val title = request.title ?: titleForTemplate(request.templateCode)
            val pushResult = pushNotificationService.sendToUser(
                userId = request.userId,
                title = title,
                body = request.message,
                templateCode = request.templateCode,
                referenceId = request.referenceId,
            )
            if (pushResult.delivered) return pushResult
            if (deliveryMode.equals("EXPO_FCM", ignoreCase = true)) {
                return pushResult
            }
        } else if (deliveryMode.equals("EXPO_FCM", ignoreCase = true)) {
            return NotificationDeliveryResult(
                delivered = false,
                provider = "EXPO_FCM",
                retryable = true,
                failureReason = "Expo/FCM push token registration is not configured for this user.",
            )
        }

        if (!deliveryMode.equals("LOGGED_DEV", ignoreCase = true)) {
            return NotificationDeliveryResult(
                delivered = false,
                provider = deliveryMode,
                retryable = false,
                failureReason = "Unsupported notification delivery mode: $deliveryMode",
            )
        }

        log.info(
            "[LOGGED_DEV_REMINDER] user={} reference={} template={} message={}",
            request.userId,
            request.referenceId,
            request.templateCode,
            request.message,
        )
        return NotificationDeliveryResult(delivered = true, provider = "LOGGED_DEV")
    }

    private fun titleForTemplate(templateCode: String): String = when (templateCode) {
        "VACCINATION_DUE", "VACCINATION_DUE_7D", "VACCINATION_DUE_1D" -> "Vaccination reminder"
        "APPOINTMENT_T24H", "APPOINTMENT_T1H" -> "Appointment reminder"
        "MERCHANT_ORDER_ALERT" -> "New order received!"
        else -> "PawsNearMe"
    }
}
