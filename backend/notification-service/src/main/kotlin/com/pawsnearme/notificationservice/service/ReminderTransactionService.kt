package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ReminderTransactionService(
    private val reminderRepo: ScheduledReminderRepository
) {

    @Transactional(readOnly = true)
    fun findDueReminders(now: Instant): List<ScheduledReminder> {
        return reminderRepo.findDueReminders(now)
    }

    @Transactional
    fun markAttempted(id: UUID, status: ReminderDeliveryStatus, attemptedAt: Instant) {
        reminderRepo.markAttempted(id, status, attemptedAt)
    }

    @Transactional
    fun markDelivered(id: UUID, status: ReminderDeliveryStatus, provider: String, deliveredAt: Instant) {
        reminderRepo.markDelivered(id, status, provider, deliveredAt)
    }

    @Transactional
    fun markFailed(id: UUID, status: ReminderDeliveryStatus, provider: String, retryable: Boolean, failureReason: String?) {
        reminderRepo.markFailed(id, status, provider, retryable, failureReason)
    }
}
