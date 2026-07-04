package com.pawsnearme.providerservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "profiles", schema = "identity")
class Profile(
    @Id
    @Column(name = "user_id")
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: UserRole,

    @Column(name = "full_name", nullable = false)
    var fullName: String,

    @Column(name = "phone_number", nullable = false, unique = true)
    var phoneNumber: String,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "preferred_locale", nullable = false)
    var preferredLocale: String = "en",

    @Column(name = "suspended", nullable = false)
    var suspended: Boolean = false,

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

@Entity
@Table(name = "addresses", schema = "identity")
class Address(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "address_id")
    var addressId: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "label")
    var label: String? = null,

    @Column(name = "line1", nullable = false)
    var line1: String,

    @Column(name = "line2")
    var line2: String? = null,

    @Column(name = "city", nullable = false)
    var city: String,

    @Column(name = "state", nullable = false)
    var state: String,

    @Column(name = "pincode", nullable = false)
    var pincode: String,

    @Column(name = "geo_lat", nullable = false)
    var geoLat: java.math.BigDecimal,

    @Column(name = "geo_lng", nullable = false)
    var geoLng: java.math.BigDecimal,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
