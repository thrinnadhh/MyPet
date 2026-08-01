package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.VaccinationReminder
import org.springframework.stereotype.Service
import java.util.UUID

data class VaccinationReminderEventPayload(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val reminderId: UUID,
    val ownerId: UUID,
    val petId: UUID,
    val vaccineName: String,
    val dueDate: String,
    val enabled: Boolean,
)

@Service
class VaccinationReminderPublisher(
    private val outboxService: OutboxService,
) {
    fun publish(reminder: VaccinationReminder, eventType: String) {
        val reminderId = reminder.reminderId ?: return
        val event = VaccinationReminderEventPayload(
            eventType = eventType,
            reminderId = reminderId,
            ownerId = reminder.ownerId,
            petId = reminder.petId,
            vaccineName = reminder.vaccineName,
            dueDate = reminder.dueDate.toString(),
            enabled = reminder.enabled,
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "VACCINATION",
            aggregateId = reminderId,
            eventType = eventType,
            eventPayload = event,
        )
    }
}
