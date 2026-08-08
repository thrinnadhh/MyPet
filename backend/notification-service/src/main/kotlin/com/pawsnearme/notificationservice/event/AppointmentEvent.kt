package com.pawsnearme.notificationservice.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Mirrors appointment-service events published on the "appointments.events" topic.
 * Fields that are absent from status-change events are nullable so the listener can
 * safely consume both AppointmentBooked and AppointmentStatusChanged messages.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppointmentEvent(
    @JsonProperty("event_id")
    val eventId: UUID = UUID.randomUUID(),
    @JsonProperty("event_type")
    val eventType: String,
    @JsonProperty("appointment_id")
    val appointmentId: UUID,
    @JsonProperty("customer_id")
    val customerId: UUID? = null,
    @JsonProperty("provider_id")
    val providerId: UUID? = null,
    @JsonProperty("slot_id")
    val slotId: UUID,
    @JsonProperty("slot_start")
    val slotStart: Instant? = null,
    @JsonProperty("price_amount")
    val priceAmount: BigDecimal? = null,
    @JsonProperty("to_status")
    val toStatus: String? = null,
    @JsonProperty("occurred_at")
    val occurredAt: Instant = Instant.now(),
)
