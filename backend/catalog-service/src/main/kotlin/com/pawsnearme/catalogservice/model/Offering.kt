package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "offerings", schema = "catalog")
class Offering(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "offering_id")
    var offeringId: UUID? = null,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "category")
    var category: String? = null,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(name = "list_price", precision = 10, scale = 2)
    var listPrice: BigDecimal? = null,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OfferingStatus = OfferingStatus.ACTIVE,

    @Column(name = "stock_quantity")
    var stockQuantity: Int? = null,

    @Column(name = "sku")
    var sku: String? = null,

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,

    @Column(name = "barcode")
    var barcode: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}
