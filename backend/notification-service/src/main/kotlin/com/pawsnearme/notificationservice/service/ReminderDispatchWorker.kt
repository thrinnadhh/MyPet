package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Polls every 5 seconds for due reminders and dispatches them.
 * The default local adapter logs delivery; production modes must fail visibly until push tokens are configured.
 */
@Service
class ReminderDispatchWorker(
    private val transactionService: ReminderTransactionService,
    private val deliveryAdapter: NotificationDeliveryAdapter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5_000)
    fun dispatchDueReminders() {
        val due = transactionService.findDueReminders(Instant.now())
        if (due.isEmpty()) return

        log.info("Dispatching ${due.size} due reminder(s)")
        due.forEach { reminder ->
            try {
                transactionService.markAttempted(reminder.id, ReminderDeliveryStatus.ATTEMPTED, Instant.now())
                val result = deliveryAdapter.deliver(
                    NotificationDeliveryRequest(
                        userId = reminder.userId,
                        referenceId = reminder.referenceId,
                        referenceType = reminder.referenceType,
                        templateCode = reminder.templateCode,
                        message = messageFor(reminder.templateCode, reminder.referenceId.toString())
                    )
                )
                if (result.delivered) {
                    val deliveredStatus = if (result.provider == "LOGGED_DEV") {
                        ReminderDeliveryStatus.DELIVERED_LOGGED
                    } else {
                        ReminderDeliveryStatus.DELIVERED
                    }
                    transactionService.markDelivered(reminder.id, deliveredStatus, result.provider, Instant.now())
                    log.info("Delivered reminder {} via {} for user {}", reminder.id, result.provider, reminder.userId)
                } else {
                    transactionService.markFailed(
                        reminder.id,
                        ReminderDeliveryStatus.FAILED,
                        result.provider,
                        result.retryable,
                        result.failureReason
                    )
                    log.warn(
                        "Reminder {} delivery failed via {} retryable={} reason={}",
                        reminder.id,
                        result.provider,
                        result.retryable,
                        result.failureReason
                    )
                }
            } catch (e: Exception) {
                log.error("Failed to process reminder {}: {}", reminder.id, e.message, e)
            }
        }
    }

    private fun messageFor(templateCode: String, referenceId: String): String {
        return when (templateCode) {
            "APPOINTMENT_T24H" -> "Your appointment is tomorrow! Ref: $referenceId"
            "APPOINTMENT_T1H"  -> "Your appointment is in 1 hour! Ref: $referenceId"
            else               -> "Reminder for: $referenceId"
        }
    }
}
