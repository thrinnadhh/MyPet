package com.pawsnearme.notificationservice.repository

import com.pawsnearme.notificationservice.model.ScheduledReminder
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

    @Modifying
    @Query("UPDATE ScheduledReminder r SET r.fired = true WHERE r.id = :id")
    fun markFired(id: UUID): Int
}
