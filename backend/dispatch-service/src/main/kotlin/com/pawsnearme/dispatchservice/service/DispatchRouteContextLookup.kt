package com.pawsnearme.dispatchservice.service

import jakarta.persistence.EntityManager
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DispatchRouteContext(
    val merchantName: String,
    val pickupAddress: String,
    val pickupLatitude: Double,
    val pickupLongitude: Double,
    val dropAddress: String,
    val dropLatitude: Double,
    val dropLongitude: Double,
    val pickupDistanceKm: Double?,
    val pickupEtaMinutes: Int?,
    val deliveryDistanceKm: Double,
    val deliveryEtaMinutes: Int,
)

@Component
class DispatchRouteContextLookup(
    private val entityManager: EntityManager,
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private const val GEO_KEY = "captains:locations"
        private const val ROUTE_FACTOR = 1.20
        private const val ESTIMATED_SPEED_KMH = 25.0
    }

    fun forOrder(orderId: UUID, captainId: UUID): DispatchRouteContext? {
        val rows = entityManager.createNativeQuery(
            """
                SELECT p.name,
                       p.address_line,
                       p.city,
                       p.pincode,
                       ST_Y(CAST(p.geo_location AS geometry)) AS pickup_lat,
                       ST_X(CAST(p.geo_location AS geometry)) AS pickup_lng,
                       a.line1,
                       a.line2,
                       a.city,
                       a.state,
                       a.pincode,
                       a.geo_lat,
                       a.geo_lng
                FROM orders.orders o
                JOIN providers.providers p ON p.provider_id = o.provider_id
                JOIN identity.addresses a
                  ON a.address_id = o.delivery_address_id
                 AND a.user_id = o.customer_id
                WHERE o.order_id = :orderId
                LIMIT 1
            """.trimIndent()
        )
            .setParameter("orderId", orderId)
            .resultList

        val row = rows.firstOrNull() as? Array<*> ?: return null
        val pickupLat = (row[4] as Number).toDouble()
        val pickupLng = (row[5] as Number).toDouble()
        val dropLat = (row[11] as Number).toDouble()
        val dropLng = (row[12] as Number).toDouble()
        val deliveryDistance = routeEstimateKm(pickupLat, pickupLng, dropLat, dropLng)

        val captainPoint = runCatching {
            redisTemplate.opsForGeo().position(GEO_KEY, captainId.toString())?.firstOrNull()
        }.getOrNull()
        val pickupDistance = captainPoint?.let { point ->
            routeEstimateKm(point.y, point.x, pickupLat, pickupLng)
        }

        return DispatchRouteContext(
            merchantName = row[0] as String,
            pickupAddress = listOfNotNull(row[1] as? String, row[2] as? String, row[3] as? String)
                .filter { it.isNotBlank() }
                .joinToString(", "),
            pickupLatitude = pickupLat,
            pickupLongitude = pickupLng,
            dropAddress = listOfNotNull(row[6] as? String, row[7] as? String, row[8] as? String, row[9] as? String, row[10] as? String)
                .filter { it.isNotBlank() }
                .joinToString(", "),
            dropLatitude = dropLat,
            dropLongitude = dropLng,
            pickupDistanceKm = pickupDistance?.roundDistance(),
            pickupEtaMinutes = pickupDistance?.let(::etaMinutes),
            deliveryDistanceKm = deliveryDistance.roundDistance(),
            deliveryEtaMinutes = etaMinutes(deliveryDistance),
        )
    }

    private fun routeEstimateKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c * ROUTE_FACTOR
    }

    private fun etaMinutes(distanceKm: Double): Int =
        ceil((distanceKm / ESTIMATED_SPEED_KMH) * 60.0).toInt().coerceAtLeast(1)

    private fun Double.roundDistance(): Double = kotlin.math.round(this * 10.0) / 10.0
}
