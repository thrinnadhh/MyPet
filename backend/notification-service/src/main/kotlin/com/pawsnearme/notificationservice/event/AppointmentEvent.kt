package com.pawsnearme.notificationservice.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant
import java.util.UUID

/**
 * Mirrors the AppointmentEvent published by appointment-service on the
 * "appointments.events" topic (CONFIRMED status triggers reminder scheduling).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppointmentEvent(
    val eventType: String,            // e.g. "APPOINTMENT_CONFIRMED"
    val appointmentId: UUID,
    val customerId: UUID,
    val slotId: UUID,
    val providerName: String,
    val slotStartsAt: Instant,
    val occurredAt: Instant = Instant.now()
)
