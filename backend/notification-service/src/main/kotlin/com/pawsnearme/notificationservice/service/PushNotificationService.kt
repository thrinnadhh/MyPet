package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.repository.DevicePushTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PushNotificationService(
    private val pushTokenRepository: DevicePushTokenRepository,
    private val expoPushClient: ExpoPushClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendToUser(
        userId: UUID,
        title: String,
        body: String,
        templateCode: String,
        referenceId: UUID? = null,
        sound: String? = null,
        channelId: String? = null,
    ): NotificationDeliveryResult {
        val tokens = pushTokenRepository.findByUserId(userId).map { it.expoPushToken }
        if (tokens.isEmpty()) {
            return NotificationDeliveryResult(
                delivered = false,
                provider = "EXPO",
                retryable = true,
                failureReason = "No push tokens registered for user",
            )
        }

        val resolvedSound = sound ?: soundForTemplate(templateCode)
        val resolvedChannel = channelId ?: channelForTemplate(templateCode)
        val data = buildMap {
            put("templateCode", templateCode)
            if (referenceId != null) put("referenceId", referenceId.toString())
        }

        val result = expoPushClient.send(
            tokens = tokens,
            title = title,
            body = body,
            sound = resolvedSound,
            data = data,
            channelId = resolvedChannel,
        )

        log.info(
            "Push to user {} template={} sent={} reason={}",
            userId,
            templateCode,
            result.sentCount,
            result.failureReason,
        )

        return NotificationDeliveryResult(
            delivered = result.success,
            provider = "EXPO",
            retryable = !result.success,
            failureReason = result.failureReason,
        )
    }

    fun sendMerchantOrderAlert(userId: UUID, orderId: UUID, amount: String) {
        sendToUser(
            userId = userId,
            title = "New order received!",
            body = "Order #${orderId.toString().take(8)} · ₹$amount — pack before pickup",
            templateCode = "MERCHANT_ORDER_ALERT",
            referenceId = orderId,
            sound = "order_alert.wav",
            channelId = "merchant-orders",
        )
    }

    private fun soundForTemplate(templateCode: String): String = when (templateCode) {
        "MERCHANT_ORDER_ALERT" -> "order_alert.wav"
        else -> "default"
    }

    private fun channelForTemplate(templateCode: String): String? = when (templateCode) {
        "MERCHANT_ORDER_ALERT" -> "merchant-orders"
        else -> null
    }
}
