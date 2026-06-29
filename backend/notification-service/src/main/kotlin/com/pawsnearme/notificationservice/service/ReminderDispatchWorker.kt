package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Polls every 5 seconds for due reminders and dispatches them.
 * Production: call FCM/APNs or SMS gateway per templateCode.
 * Current: log-only stub.
 */
@Service
class ReminderDispatchWorker(
    private val reminderRepo: ScheduledReminderRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    fun dispatchDueReminders() {
        val due = reminderRepo.findDueReminders(Instant.now())
        if (due.isEmpty()) return

        log.info("Dispatching ${due.size} due reminder(s)")
        due.forEach { reminder ->
            sendNotification(reminder.userId.toString(), reminder.templateCode, reminder.referenceId.toString())
            reminderRepo.markFired(reminder.id)
            log.info("Fired reminder ${reminder.id} [${reminder.templateCode}] for user ${reminder.userId}")
        }
    }

    /** Stub: replace with real push/SMS gateway in production. */
    private fun sendNotification(userId: String, templateCode: String, referenceId: String) {
        val message = when (templateCode) {
            "APPOINTMENT_T24H" -> "Your appointment is tomorrow! Ref: $referenceId"
            "APPOINTMENT_T1H"  -> "Your appointment is in 1 hour! Ref: $referenceId"
            else               -> "Reminder for: $referenceId"
        }
        log.info("[PUSH STUB] → user=$userId | $message")
    }
}
