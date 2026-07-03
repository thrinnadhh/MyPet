package com.pawsnearme.common.idempotency

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "processed_events")
class ProcessedEvent(
    @Id
    @Column(name = "event_id")
    var eventId: UUID,

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now()
)
