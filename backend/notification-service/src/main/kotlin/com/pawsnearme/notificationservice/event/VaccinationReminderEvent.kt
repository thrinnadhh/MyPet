package com.pawsnearme.notificationservice.event

import java.time.LocalDate
import java.util.UUID

data class VaccinationReminderEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val reminderId: UUID,
    val ownerId: UUID,
    val petId: UUID,
    val vaccineName: String,
    val dueDate: LocalDate,
    val enabled: Boolean,
)
