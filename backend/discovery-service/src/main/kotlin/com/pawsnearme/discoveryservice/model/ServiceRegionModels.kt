package com.pawsnearme.discoveryservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class RegionStatus {
    ENABLED, PAUSED, DISABLED
}

@Entity
@Table(name = "service_regions", schema = "providers")
class ServiceRegion(
    @Id
    @Column(name = "id")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "city_identity", nullable = false, unique = true)
    var cityIdentity: String,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Column(name = "state", nullable = false)
    var state: String,

    @Column(name = "country", nullable = false)
    var country: String = "India",

    @Column(name = "center_latitude", nullable = false)
    var centerLatitude: Double,

    @Column(name = "center_longitude", nullable = false)
    var centerLongitude: Double,

    @Column(name = "radius_km", nullable = false)
    var radiusKm: Double = 25.0,

    @Column(name = "pincodes")
    var pincodes: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RegionStatus = RegionStatus.ENABLED,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 1,

    @Column(name = "allow_products", nullable = false)
    var allowProducts: Boolean = true,

    @Column(name = "allow_grooming", nullable = false)
    var allowGrooming: Boolean = true,

    @Column(name = "allow_vet", nullable = false)
    var allowVet: Boolean = true,

    @Column(name = "allow_own_delivery", nullable = false)
    var allowOwnDelivery: Boolean = true,

    @Column(name = "allow_3p_delivery", nullable = false)
    var allow3pDelivery: Boolean = true,

    @Column(name = "allow_cod", nullable = false)
    var allowCod: Boolean = true,

    @Column(name = "allow_online_payment", nullable = false)
    var allowOnlinePayment: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

data class CreateServiceRegionRequest(
    val cityIdentity: String,
    val displayName: String,
    val state: String,
    val country: String = "India",
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusKm: Double = 25.0,
    val pincodes: String? = null,
    val status: RegionStatus = RegionStatus.ENABLED,
    val sortOrder: Int = 1,
    val allowProducts: Boolean = true,
    val allowGrooming: Boolean = true,
    val allowVet: Boolean = true,
    val allowOwnDelivery: Boolean = true,
    val allow3pDelivery: Boolean = true,
    val allowCod: Boolean = true,
    val allowOnlinePayment: Boolean = true
)

data class UpdateServiceRegionRequest(
    val displayName: String? = null,
    val state: String? = null,
    val country: String? = null,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val radiusKm: Double? = null,
    val pincodes: String? = null,
    val status: RegionStatus? = null,
    val sortOrder: Int? = null,
    val allowProducts: Boolean? = null,
    val allowGrooming: Boolean? = null,
    val allowVet: Boolean? = null,
    val allowOwnDelivery: Boolean? = null,
    val allow3pDelivery: Boolean? = null,
    val allowCod: Boolean? = null,
    val allowOnlinePayment: Boolean? = null
)

data class ServiceabilityCheckResult(
    val serviceable: Boolean,
    val region: ServiceRegionDto? = null,
    val reason: String? = null
)

data class ServiceRegionDto(
    val id: UUID,
    val cityIdentity: String,
    val displayName: String,
    val state: String,
    val country: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusKm: Double,
    val pincodes: List<String>,
    val status: RegionStatus,
    val sortOrder: Int,
    val featureFlags: FeatureFlagsDto
)

data class FeatureFlagsDto(
    val allowProducts: Boolean,
    val allowGrooming: Boolean,
    val allowVet: Boolean,
    val allowOwnDelivery: Boolean,
    val allow3pDelivery: Boolean,
    val allowCod: Boolean,
    val allowOnlinePayment: Boolean
)

fun ServiceRegion.toDto(): ServiceRegionDto {
    val pincodeList = pincodes?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    return ServiceRegionDto(
        id = id,
        cityIdentity = cityIdentity,
        displayName = displayName,
        state = state,
        country = country,
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude,
        radiusKm = radiusKm,
        pincodes = pincodeList,
        status = status,
        sortOrder = sortOrder,
        featureFlags = FeatureFlagsDto(
            allowProducts = allowProducts,
            allowGrooming = allowGrooming,
            allowVet = allowVet,
            allowOwnDelivery = allowOwnDelivery,
            allow3pDelivery = allow3pDelivery,
            allowCod = allowCod,
            allowOnlinePayment = allowOnlinePayment
        )
    )
}

data class UniversalSearchResultItem(
    val id: String,
    val type: String, // 'PRODUCT', 'PET_SHOP', 'HOSPITAL', 'GROOMER', 'SERVICE', 'GUIDE'
    val title: String,
    val subtitle: String?,
    val imageUrl: String? = null,
    val rating: String? = null,
    val price: String? = null,
    val distanceKm: Double? = null,
    val route: String,
    val isEmergency: Boolean = false
)

data class UniversalSearchResponse(
    val query: String,
    val totalResults: Int,
    val page: Int,
    val size: Int,
    val results: List<UniversalSearchResultItem>
)
