package com.pawsnearme.notificationservice.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/**
 * Mirrors the AppointmentEvent published by appointment-service on the
 * "appointments.events" topic (CONFIRMED status triggers reminder scheduling).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppointmentEvent(
    @JsonProperty("event_type")
    val eventType: String,
    @JsonProperty("appointment_id")
    val appointmentId: UUID,
    @JsonProperty("customer_id")
    val customerId: UUID,
    @JsonProperty("slot_id")
    val slotId: UUID,
    @JsonProperty("slot_start")
    val slotStart: Instant? = null,
    @JsonProperty("to_status")
    val toStatus: String? = null,
    @JsonProperty("occurred_at")
    val occurredAt: Instant = Instant.now()
)
