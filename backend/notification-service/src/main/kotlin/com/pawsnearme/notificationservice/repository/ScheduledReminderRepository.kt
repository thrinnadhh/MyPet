package com.pawsnearme.notificationservice.repository

import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ScheduledReminderRepository : JpaRepository<ScheduledReminder, UUID> {

    /** All unfired reminders whose fire_at has passed. */
    @Query("SELECT r FROM ScheduledReminder r WHERE r.fired = false AND r.fireAt <= :now")
    fun findDueReminders(now: Instant): List<ScheduledReminder>

    /** Guard against duplicate scheduling for same reference + template. */
    fun existsByReferenceIdAndTemplateCode(referenceId: UUID, templateCode: String): Boolean

    fun deleteByReferenceIdAndReferenceType(referenceId: UUID, referenceType: String): Int

    @Modifying
    @Query("UPDATE ScheduledReminder r SET r.deliveryStatus = :status, r.attemptCount = r.attemptCount + 1, r.lastAttemptAt = :attemptedAt, r.failureReason = null, r.retryableFailure = false WHERE r.id = :id")
    fun markAttempted(id: UUID, status: ReminderDeliveryStatus, attemptedAt: Instant): Int

    @Modifying
    @Query("UPDATE ScheduledReminder r SET r.fired = true, r.deliveryStatus = :status, r.provider = :provider, r.deliveredAt = :deliveredAt, r.failureReason = null, r.retryableFailure = false WHERE r.id = :id")
    fun markDelivered(id: UUID, status: ReminderDeliveryStatus, provider: String, deliveredAt: Instant): Int

    @Modifying
    @Query("UPDATE ScheduledReminder r SET r.deliveryStatus = :status, r.provider = :provider, r.retryableFailure = :retryable, r.failureReason = :failureReason WHERE r.id = :id")
    fun markFailed(id: UUID, status: ReminderDeliveryStatus, provider: String, retryable: Boolean, failureReason: String?): Int
}
