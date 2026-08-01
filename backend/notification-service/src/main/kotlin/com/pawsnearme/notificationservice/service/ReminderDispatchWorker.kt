package com.pawsnearme.notificationservice.service

import com.pawsnearme.common.scheduling.WorkerScheduler
import com.pawsnearme.notificationservice.config.NotificationTemplateProperties
import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@WorkerScheduler
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
                val reminderId = requireNotNull(reminder.id) {
                    "Persisted reminder for ${reminder.referenceId} has no reminder ID"
                }
                transactionService.markAttempted(reminderId, ReminderDeliveryStatus.ATTEMPTED, Instant.now())
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
                    transactionService.markDelivered(reminderId, deliveredStatus, result.provider, Instant.now())
                    log.info("Delivered reminder {} via {} for user {}", reminderId, result.provider, reminder.userId)
                } else {
                    transactionService.markFailed(
                        reminderId,
                        ReminderDeliveryStatus.FAILED,
                        result.provider,
                        result.retryable,
                        result.failureReason
                    )
                    log.warn(
                        "Reminder {} delivery failed via {} retryable={} reason={}",
                        reminderId,
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
