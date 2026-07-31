package com.pawsnearme.catalogservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "internal_stock_mutations", schema = "catalog")
class InternalStockMutation(
    @Id
    @Column(name = "idempotency_key", nullable = false)
    var idempotencyKey: UUID,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "operation", nullable = false, length = 16)
    var operation: String,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
