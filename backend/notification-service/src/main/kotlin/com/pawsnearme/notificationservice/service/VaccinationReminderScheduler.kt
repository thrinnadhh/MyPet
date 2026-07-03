package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.event.VaccinationReminderEvent
import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

@Service
class VaccinationReminderScheduler(
    private val reminderRepo: ScheduledReminderRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone = ZoneId.of("Asia/Kolkata")

    @Transactional
    fun apply(event: VaccinationReminderEvent) {
        reminderRepo.deleteByReferenceIdAndReferenceType(event.reminderId, "VACCINATION")

        if (!event.enabled) {
            log.info("Vaccination reminders cleared for {}", event.reminderId)
            return
        }

        val dueAt = event.dueDate.atStartOfDay(zone).toInstant()
        scheduleReminder(event, "VACCINATION_DUE_7D", dueAt.minusSeconds(7 * 24 * 3600))
        scheduleReminder(event, "VACCINATION_DUE_1D", dueAt.minusSeconds(24 * 3600))
        scheduleReminder(event, "VACCINATION_DUE", dueAt)
    }

    private fun scheduleReminder(event: VaccinationReminderEvent, templateCode: String, fireAt: Instant) {
        if (fireAt.isBefore(Instant.now())) {
            log.debug("Skipping past vaccination reminder {} for {}", templateCode, event.reminderId)
            return
        }
        reminderRepo.save(
            ScheduledReminder(
                userId = event.ownerId,
                referenceType = "VACCINATION",
                referenceId = event.reminderId,
                fireAt = fireAt,
                templateCode = templateCode,
            ),
        )
        log.info("Scheduled {} for vaccination {} at {}", templateCode, event.reminderId, fireAt)
    }
}
