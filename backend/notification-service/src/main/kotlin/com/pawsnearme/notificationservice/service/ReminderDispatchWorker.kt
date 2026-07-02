package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Polls every 5 seconds for due reminders and dispatches them.
 * The default local adapter logs delivery; production modes must fail visibly until push tokens are configured.
 */
@Service
class ReminderDispatchWorker(
    private val reminderRepo: ScheduledReminderRepository,
    private val deliveryAdapter: NotificationDeliveryAdapter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    fun dispatchDueReminders() {
        val due = reminderRepo.findDueReminders(Instant.now())
        if (due.isEmpty()) return

        log.info("Dispatching ${due.size} due reminder(s)")
        due.forEach { reminder ->
            reminderRepo.markAttempted(reminder.id, ReminderDeliveryStatus.ATTEMPTED, Instant.now())
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
                reminderRepo.markDelivered(reminder.id, deliveredStatus, result.provider, Instant.now())
                log.info("Delivered reminder {} via {} for user {}", reminder.id, result.provider, reminder.userId)
            } else {
                reminderRepo.markFailed(
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
