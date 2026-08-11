package com.pawsnearme.captainservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.captainservice.model.CaptainDocument
import com.pawsnearme.captainservice.model.CaptainEarning
import com.pawsnearme.captainservice.model.CaptainProfile
import com.pawsnearme.captainservice.model.CaptainStatus
import com.pawsnearme.captainservice.model.VehicleType
import com.pawsnearme.captainservice.repository.CaptainDocumentRepository
import com.pawsnearme.captainservice.repository.CaptainEarningRepository
import com.pawsnearme.captainservice.repository.CaptainProfileRepository
import com.pawsnearme.captainservice.security.BankDataCipher
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

@Service
@Transactional
class CaptainService(
    private val profileRepository: CaptainProfileRepository,
    private val earningRepository: CaptainEarningRepository,
    private val documentRepository: CaptainDocumentRepository,
    private val redisTemplate: StringRedisTemplate,
    private val bankDataCipher: BankDataCipher,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CaptainService::class.java)
        private const val GEO_KEY = "captains:locations"
        private const val ONLINE_KEY = "captains:online"
        private const val LOCATION_FRESH_PREFIX = "captains:location:fresh:"
        private val LOCATION_FRESHNESS = Duration.ofSeconds(60)
    }

    fun onboardCaptain(
        captainId: UUID,
        vehicleType: VehicleType,
        vehicleNumber: String?,
        licenseDocUrl: String?,
        bankAccount: String?,
        bankIfsc: String?,
        selfieDocUrl: String?,
        documents: List<Pair<String, String>>,
    ): CaptainProfile {
        val existing = profileRepository.findById(captainId).orElse(null)
        val profile = existing ?: CaptainProfile(
            captainId = captainId,
            status = CaptainStatus.PENDING_APPROVAL,
            vehicleType = vehicleType,
        )
        profile.status = CaptainStatus.PENDING_APPROVAL
        profile.vehicleType = vehicleType
        profile.vehicleNumber = vehicleNumber
        profile.licenseDocUrl = licenseDocUrl
        profile.bankAccount = bankDataCipher.encrypt(bankAccount)
        profile.bankIfsc = bankDataCipher.encrypt(bankIfsc)
        profile.selfieDocUrl = selfieDocUrl
        clearAvailability(captainId)
        val saved = profileRepository.save(profile)
        documents.forEach { (type, url) ->
            documentRepository.save(
                CaptainDocument(captainId = captainId, docType = type, docUrl = url)
            )
        }
        return saved
    }

    fun listPendingCaptains(): List<CaptainProfile> =
        profileRepository.findAll().filter { it.status == CaptainStatus.PENDING_APPROVAL }

    fun approveCaptain(captainId: UUID): CaptainProfile {
        val profile = getProfile(captainId)
        profile.status = CaptainStatus.ACTIVE
        return profileRepository.save(profile)
    }

    fun rejectCaptain(captainId: UUID): CaptainProfile {
        val profile = getProfile(captainId)
        profile.status = CaptainStatus.REJECTED
        clearAvailability(captainId)
        return profileRepository.save(profile)
    }

    fun getDocuments(captainId: UUID): List<CaptainDocument> =
        documentRepository.findByCaptainId(captainId)

    @Transactional(readOnly = true)
    fun getProfile(captainId: UUID): CaptainProfile =
        profileRepository.findById(captainId)
            .orElseThrow { NoSuchElementException("Captain profile not found for ID $captainId") }

    fun toggleOnlineStatus(
        captainId: UUID,
        online: Boolean,
        longitude: Double?,
        latitude: Double?,
    ): String {
        val profile = getProfile(captainId)
        if (profile.status != CaptainStatus.ACTIVE) {
            clearAvailability(captainId)
            throw IllegalStateException("Captain account is not active.")
        }

        if (online) {
            if (longitude == null || latitude == null) {
                throw IllegalArgumentException("Coordinates required to go online.")
            }
            redisTemplate.opsForGeo().add(
                GEO_KEY,
                RedisPoint(longitude, latitude),
                captainId.toString(),
            )
            redisTemplate.opsForSet().add(ONLINE_KEY, captainId.toString())
            touchLocationFreshness(captainId)
            return "ONLINE"
        }

        clearAvailability(captainId)
        return "OFFLINE"
    }

    fun updateLocation(captainId: UUID, longitude: Double, latitude: Double) {
        val profile = getProfile(captainId)
        if (profile.status != CaptainStatus.ACTIVE) {
            clearAvailability(captainId)
            throw IllegalStateException("Captain account is not active.")
        }
        if (redisTemplate.opsForSet().isMember(ONLINE_KEY, captainId.toString()) != true) {
            throw IllegalStateException("Captain must be online before location updates are accepted.")
        }
        redisTemplate.opsForGeo().add(
            GEO_KEY,
            RedisPoint(longitude, latitude),
            captainId.toString(),
        )
        touchLocationFreshness(captainId)
    }

    @Transactional(readOnly = true)
    fun getEarnings(captainId: UUID): List<CaptainEarning> =
        earningRepository.findByCaptainId(captainId)

    @KafkaListener(topics = ["orders.events"], groupId = "captain-service-group-v2")
    fun handleOrderStatusChanged(record: ConsumerRecord<String, String>) {
        val event: Map<String, Any> = try {
            ObjectMapper().readValue(record.value(), object : TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            logger.warn("Failed to parse Kafka event: {}", e.message, e)
            return
        }
        val toStatus = event["toStatus"] as? String

        if (toStatus == "DELIVERED") {
            val orderIdStr = event["orderId"] as? String ?: return
            val orderId = UUID.fromString(orderIdStr)
            val captainIdStr = event["captainId"] as? String ?: return
            val captainId = UUID.fromString(captainIdStr)

            val existing = earningRepository.findByCaptainId(captainId).any { it.orderId == orderId }
            if (existing) return

            val deliveryFeeStr = event["deliveryFee"] as? String ?: "150.00"
            val earningAmount = BigDecimal(deliveryFeeStr)
            earningRepository.save(
                CaptainEarning(
                    captainId = captainId,
                    orderId = orderId,
                    amount = earningAmount,
                )
            )

            profileRepository.findById(captainId).ifPresent { profile ->
                profile.totalDeliveries += 1
                profileRepository.save(profile)
                logger.info(
                    "Recorded earning of {} for Captain {} on Order {}.",
                    earningAmount,
                    captainId,
                    orderId,
                )
            }
        }
    }

    private fun touchLocationFreshness(captainId: UUID) {
        redisTemplate.opsForValue().set(
            "$LOCATION_FRESH_PREFIX$captainId",
            "fresh",
            LOCATION_FRESHNESS,
        )
    }

    private fun clearAvailability(captainId: UUID) {
        runCatching { redisTemplate.opsForZSet().remove(GEO_KEY, captainId.toString()) }
        runCatching { redisTemplate.opsForSet().remove(ONLINE_KEY, captainId.toString()) }
        runCatching { redisTemplate.delete("$LOCATION_FRESH_PREFIX$captainId") }
    }
}
