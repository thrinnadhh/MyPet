package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.config.NotificationTemplateProperties
import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Polls every 5 seconds for due reminders and dispatches them.
 * Message text is resolved from [NotificationTemplateProperties] (application.yml),
 * so new template types require only a config change, not a code change.
 */
@Service
class ReminderDispatchWorker(
    private val transactionService: ReminderTransactionService,
    private val deliveryAdapter: NotificationDeliveryAdapter,
    private val templateProperties: NotificationTemplateProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(
        name = "notification-dispatch-due-reminders",
        lockAtMostFor = "PT2M",
        lockAtLeastFor = "PT1S"
    )
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
                        message = templateProperties.messageFor(reminder.templateCode, reminder.referenceId.toString())
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
}
