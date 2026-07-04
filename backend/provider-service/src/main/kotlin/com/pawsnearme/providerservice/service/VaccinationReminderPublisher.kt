package com.pawsnearme.providerservice.service

import com.pawsnearme.providerservice.model.VaccinationReminder
import org.springframework.kafka.core.KafkaTemplate
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
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    fun publish(reminder: VaccinationReminder, eventType: String) {
        val reminderId = reminder.reminderId ?: return
        kafkaTemplate.send(
            "vaccination.events",
            reminderId.toString(),
            VaccinationReminderEventPayload(
                eventType = eventType,
                reminderId = reminderId,
                ownerId = reminder.ownerId,
                petId = reminder.petId,
                vaccineName = reminder.vaccineName,
                dueDate = reminder.dueDate.toString(),
                enabled = reminder.enabled,
            ),
        )
    }
}
