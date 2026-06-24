package com.pawsnearme.discoveryservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.locationtech.jts.geom.Point

enum class ProviderType {
    PET_STORE, VET_HOSPITAL, GROOMING_CENTER
}

enum class FulfillmentType {
    DELIVERY, APPOINTMENT
}

enum class ProviderStatus {
    DRAFT, PENDING_APPROVAL, INFO_REQUESTED, ACTIVE, SUSPENDED, REJECTED
}

@Entity
@Table(name = "providers", schema = "providers")
class Provider(
    @Id
    @Column(name = "provider_id")
    var providerId: UUID,

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

    @Column(name = "address_line", nullable = false)
    var addressLine: String,

    @Column(name = "city", nullable = false)
    var city: String,

    @Column(name = "pincode", nullable = false)
    var pincode: String,

    @Column(name = "geo_location", nullable = false, columnDefinition = "geography(Point, 4326)")
    var geoLocation: Point,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProviderStatus,

    @Column(name = "rating_avg", precision = 3, scale = 2)
    var ratingAvg: BigDecimal = BigDecimal("0.00"),

    @Column(name = "rating_count")
    var ratingCount: Int = 0,

    @Column(name = "commission_pct", precision = 5, scale = 2, nullable = false)
    var commissionPct: BigDecimal = BigDecimal("15.00"),

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant
)

data class ProviderSearchResult(
    val providerId: UUID,
    val providerType: ProviderType,
    val fulfillmentType: FulfillmentType,
    val name: String,
    val description: String?,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val longitude: Double,
    val latitude: Double,
    val ratingAvg: Double,
    val ratingCount: Int,
    val distanceKm: Double
)
