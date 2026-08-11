package com.pawsnearme.providerservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.locationtech.jts.geom.Point

/**
 * Authoritative provider aggregate owned by the provider bounded context.
 *
 * The explicit JPA entity name prevents collisions with read-only provider
 * projections owned by other modules when all contexts share one persistence
 * unit in the modular monolith. The physical table mapping is unchanged.
 */
@Entity(name = "ProviderAggregate")
@Table(name = "providers", schema = "providers")
class Provider(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "provider_id")
    var providerId: UUID? = null,

    @Column(name = "owner_user_id", nullable = false)
    var ownerUserId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    var providerType: ProviderType,

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    var fulfillmentType: FulfillmentType,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "license_number")
    var licenseNumber: String? = null,

    @Column(name = "license_doc_url")
    var licenseDocUrl: String? = null,

    @Column(name = "address_line", nullable = false)
    var addressLine: String,

    @Column(name = "city", nullable = false)
    var city: String,

    @Column(name = "pincode", nullable = false)
    var pincode: String,

    // JTS Point maps (longitude, latitude) -> x is longitude, y is latitude
    @Column(name = "geo_location", nullable = false, columnDefinition = "geography(Point, 4326)")
    var geoLocation: Point,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProviderStatus = ProviderStatus.DRAFT,

    @Column(name = "rating_avg", precision = 3, scale = 2)
    var ratingAvg: BigDecimal = BigDecimal("0.00"),

    @Column(name = "rating_count")
    var ratingCount: Int = 0,

    @Column(name = "commission_pct", precision = 5, scale = 2, nullable = false)
    var commissionPct: BigDecimal = BigDecimal("15.00"),

    @Column(name = "gst_number")
    var gstNumber: String? = null,

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
