package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "slots", schema = "catalog")
class Slot(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "slot_id")
    var slotId: UUID? = null,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "slot_start", nullable = false)
    var slotStart: Instant,

    @Column(name = "slot_end", nullable = false)
    var slotEnd: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SlotStatus = SlotStatus.AVAILABLE,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
