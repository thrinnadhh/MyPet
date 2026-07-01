package com.pawsnearme.captainservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.captainservice.model.*
import com.pawsnearme.captainservice.repository.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional
class CaptainService(
    private val profileRepository: CaptainProfileRepository,
    private val earningRepository: CaptainEarningRepository,
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
        private const val GEO_KEY = "captains:locations"
    }

    // --- Onboarding & Profile ---
    
    fun onboardCaptain(captainId: UUID, vehicleType: VehicleType, vehicleNumber: String?, licenseDocUrl: String?): CaptainProfile {
        val profile = CaptainProfile(
            captainId = captainId,
            status = CaptainStatus.ACTIVE, // Auto-approve in development
            vehicleType = vehicleType,
            vehicleNumber = vehicleNumber,
            licenseDocUrl = licenseDocUrl
        )
        return profileRepository.save(profile)
    }

    @Transactional(readOnly = true)
    fun getProfile(captainId: UUID): CaptainProfile {
        return profileRepository.findById(captainId)
            .orElseThrow { NoSuchElementException("Captain profile not found for ID $captainId") }
    }

    // --- Status & Location (Redis Geo) ---

    fun toggleOnlineStatus(captainId: UUID, online: Boolean, longitude: Double?, latitude: Double?): String {
        // Verify profile exists
        val profile = getProfile(captainId)
        if (profile.status != CaptainStatus.ACTIVE) {
            throw IllegalStateException("Captain account is not active.")
        }

        if (online) {
            if (longitude == null || latitude == null) {
                throw IllegalArgumentException("Coordinates required to go online.")
            }
            redisTemplate.opsForGeo().add(GEO_KEY, RedisPoint(longitude, latitude), captainId.toString())
            return "ONLINE"
        } else {
            redisTemplate.opsForZSet().remove(GEO_KEY, captainId.toString())
            return "OFFLINE"
        }
    }

    fun updateLocation(captainId: UUID, longitude: Double, latitude: Double) {
        // Verify profile exists
        getProfile(captainId)
        // Set coordinates in Redis Geo index
        redisTemplate.opsForGeo().add(GEO_KEY, RedisPoint(longitude, latitude), captainId.toString())
    }

    // --- Earnings ---

    @Transactional(readOnly = true)
    fun getEarnings(captainId: UUID): List<CaptainEarning> {
        return earningRepository.findByCaptainId(captainId)
    }

    // --- Kafka Event Listener ---
    @KafkaListener(topics = ["orders.events"], groupId = "captain-service-group-v2")
    fun handleOrderStatusChanged(record: ConsumerRecord<String, String>) {
        val event: Map<String, Any> = try {
            ObjectMapper().readValue(record.value(), object : TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            println("WARNING: Failed to parse Kafka event: ${e.message}")
            return
        }
        val toStatus = event["toStatus"] as? String
        
        if (toStatus == "DELIVERED") {
            val orderIdStr = event["orderId"] as? String ?: return
            val orderId = UUID.fromString(orderIdStr)
            val captainIdStr = event["captainId"] as? String ?: return
            val captainId = UUID.fromString(captainIdStr)
            
            // Check if this earning has already been recorded
            val existing = earningRepository.findByCaptainId(captainId).any { it.orderId == orderId }
            if (existing) return

            val deliveryFeeStr = event["deliveryFee"] as? String ?: "150.00"
            val earningAmount = BigDecimal(deliveryFeeStr)

            // Save Captain Earning
            val earning = CaptainEarning(
                captainId = captainId,
                orderId = orderId,
                amount = earningAmount
            )
            earningRepository.save(earning)

            // Increment deliveries count in Profile
            profileRepository.findById(captainId).ifPresent { profile ->
                profile.totalDeliveries += 1
                profileRepository.save(profile)
                println("CaptainService: Recorded earning of $earningAmount for Captain $captainId on Order $orderId.")
            }
        }
    }
}
