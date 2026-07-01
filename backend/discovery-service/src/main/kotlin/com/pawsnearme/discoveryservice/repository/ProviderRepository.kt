package com.pawsnearme.discoveryservice.repository

import com.pawsnearme.discoveryservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProviderRepository : JpaRepository<Provider, UUID> {

    fun findByStatus(status: ProviderStatus): List<Provider>

    @Query(value = """
         SELECT p.provider_id AS providerId,
               p.provider_type AS providerType,
               p.fulfillment_type AS fulfillmentType,
               p.name AS name,
               p.description AS description,
               p.address_line AS addressLine,
               p.city AS city,
               p.pincode AS pincode,
               ST_X(CAST(p.geo_location AS geometry)) AS longitude,
               ST_Y(CAST(p.geo_location AS geometry)) AS latitude,
               p.rating_avg AS ratingAvg,
               p.rating_count AS ratingCount,
               ST_Distance(p.geo_location, CAST(ST_SetSRID(ST_Point(:longitude, :latitude), 4326) AS geography)) AS distance
        FROM providers.providers p
        WHERE p.status = 'ACTIVE'
          AND (CAST(:providerType AS varchar) IS NULL OR p.provider_type = :providerType)
          AND ST_DWithin(p.geo_location, CAST(ST_SetSRID(ST_Point(:longitude, :latitude), 4326) AS geography), :radiusMeters)
        ORDER BY distance ASC
    """, nativeQuery = true)
    fun findNearbyActiveProviders(
        @Param("longitude") longitude: Double,
        @Param("latitude") latitude: Double,
        @Param("radiusMeters") radiusMeters: Double,
        @Param("providerType") providerType: String?
    ): List<ProviderDistanceProjection>
}

interface ProviderDistanceProjection {
    fun getProviderId(): UUID
    fun getProviderType(): String
    fun getFulfillmentType(): String
    fun getName(): String
    fun getDescription(): String?
    fun getAddressLine(): String
    fun getCity(): String
    fun getPincode(): String
    fun getLongitude(): Double
    fun getLatitude(): Double
    fun getRatingAvg(): java.math.BigDecimal
    fun getRatingCount(): Int
    fun getDistance(): Double
}
