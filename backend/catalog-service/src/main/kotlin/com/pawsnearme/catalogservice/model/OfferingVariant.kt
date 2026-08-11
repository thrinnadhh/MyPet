package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "offering_variants", schema = "catalog")
class OfferingVariant(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "variant_id")
    var variantId: UUID? = null,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int = 0,

    @Column(name = "sku")
    var sku: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
