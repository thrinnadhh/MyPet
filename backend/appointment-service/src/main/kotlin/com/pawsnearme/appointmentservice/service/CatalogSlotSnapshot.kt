package com.pawsnearme.appointmentservice.service

import java.time.Instant
import java.util.UUID

/**
 * Legacy transport DTO retained by the distributed appointment HTTP adapter.
 * Business logic consumes the transport-neutral common module snapshot instead.
 */
data class CatalogSlotSnapshot(
    val slotId: UUID? = null,
    val slotStart: Instant? = null,
    val slotEnd: Instant? = null,
    val status: String? = null
)
