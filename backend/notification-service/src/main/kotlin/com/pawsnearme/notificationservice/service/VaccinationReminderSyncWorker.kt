package com.pawsnearme.notificationservice.service

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.notificationservice.event.VaccinationReminderEvent
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VaccinationReminderSyncWorker(
    private val vaccinationReminderScheduler: VaccinationReminderScheduler,
    private val providerModule: ProviderModuleApi
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 30_000)
    @SchedulerLock(
        name = "notification-sync-vaccination-reminders",
        lockAtMostFor = "PT30M",
        lockAtLeastFor = "PT5M"
    )
    fun syncEnabledReminders() {
        try {
            val reminders = providerModule.enabledVaccinationReminders()
            reminders.forEach { reminder ->
                vaccinationReminderScheduler.apply(
                    VaccinationReminderEvent(
                        eventId = UUID.randomUUID(),
                        eventType = "VaccinationReminderSynced",
                        reminderId = reminder.reminderId,
                        ownerId = reminder.ownerId,
                        petId = reminder.petId,
                        vaccineName = reminder.vaccineName,
                        dueDate = reminder.dueDate,
                        enabled = reminder.enabled
                    )
                )
            }
            log.info("Synced {} vaccination reminders from provider module", reminders.size)
        } catch (e: Exception) {
            log.warn("Vaccination reminder sync skipped: {}", e.message)
        }
    }
}
