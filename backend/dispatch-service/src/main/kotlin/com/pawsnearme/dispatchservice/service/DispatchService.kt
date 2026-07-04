package com.pawsnearme.dispatchservice.service

import org.slf4j.LoggerFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.dispatchservice.model.*
import com.pawsnearme.dispatchservice.repository.*
import jakarta.persistence.EntityManager
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.retry.annotation.Backoff
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.RestOperations
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DispatchService(
    private val jobRepository: DispatchJobRepository,
    private val offerRepository: DispatchOfferRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val entityManager: EntityManager,
    private val outboxService: OutboxService,
    private val idempotencyService: IdempotencyService,
    @Value("\${ORDER_SERVICE_URL:http://localhost:8084}")
    private val orderServiceBaseUrl: String = "http://localhost:8084",
    @Value("\${gateway.trust.secret:}")
    private val gatewayTrustSecret: String = "",
    private val restTemplate: RestOperations = RestTemplate()
) {

    private val objectMapper = ObjectMapper()
    private val orderServiceUrl = "$orderServiceBaseUrl/api/v1/orders"

    companion object {
        private val logger = LoggerFactory.getLogger(DispatchService::class.java)
        private const val GEO_KEY = "captains:locations"
    }

    // --- Kafka Listener for Orders ---
    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
        dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = ["orders.events"], groupId = "dispatch-service-group-v2")
    fun handleOrderStatusChanged(record: ConsumerRecord<String, String>) {
        val event: Map<String, Any> = try {
            objectMapper.readValue(record.value(), object : TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            logger.warn("Failed to parse Kafka event: {}", e.message, e)
            return
        }

        val eventIdStr = event["eventId"] as? String ?: event["event_id"] as? String ?: return
        val eventId = try { UUID.fromString(eventIdStr) } catch(e: Exception) { return }

        // Idempotency check
        if (!idempotencyService.checkAndRecord(eventId)) {
            logger.info("Duplicate event ignored: {}", eventId)
            return
        }

        val toStatus = event["toStatus"] as? String

        if (toStatus == "READY_FOR_PICKUP") {
            val orderIdStr = event["orderId"] as? String ?: return
            val orderId = UUID.fromString(orderIdStr)
            logger.info("Received READY_FOR_PICKUP for order {}. Starting dispatch...", orderId)
            // Start dispatch process
            startDispatchProcess(orderId)
        }
    }

    fun startDispatchProcess(orderId: UUID): DispatchJob {
        // Prevent duplicate jobs
        val existing = jobRepository.findByOrderId(orderId)
        if (existing != null) return existing

        val job = DispatchJob(
            orderId = orderId,
            status = JobStatus.PENDING_ASSIGNMENT,
            attemptCount = 0,
            pickupOtp = (1000..9999).random().toString(),
            deliveryOtp = (1000..9999).random().toString()
        )
        val savedJob = jobRepository.save(job)
        triggerNextOffer(savedJob)
        return savedJob
    }

    fun triggerNextOffer(job: DispatchJob) {
        if (job.attemptCount >= job.maxAttempts) {
            job.status = JobStatus.FAILED
            job.resolvedAt = Instant.now()
            jobRepository.save(job)
            logger.error("Failed to assign order {} after {} attempts.", job.orderId, job.maxAttempts)
            
            publishDispatchEvent("DispatchJobFailed", job, null, mapOf("reason" to "MAX_ATTEMPTS_EXHAUSTED"))
            return
        }

        // 1. Get Provider coordinates from DB
        val providerCoords = getProviderCoordinates(job.orderId)
        if (providerCoords == null) {
            logger.error("Coordinates for order {} not found. Failing job.", job.orderId)
            job.status = JobStatus.FAILED
            jobRepository.save(job)
            return
        }
        val (lng, lat) = providerCoords

        // 2. Query Redis Geo for active Captains within 5 km
        val results = try {
            val args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()

            redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(RedisPoint(lng, lat)),
                Distance(5.0, Metrics.KILOMETERS),
                args
            )
        } catch (e: Exception) {
            logger.warn("Redis Geo lookup failed: {}", e.message, e)
            null
        }

        val onlineCaptains = results?.content?.map { UUID.fromString(it.content.name) } ?: emptyList()

        // 3. Find candidates who haven't rejected/timed out this job yet
        val existingOffers = offerRepository.findByJobId(job.jobId!!)
        val attemptedCaptains = existingOffers.map { it.captainId }.toSet()

        val candidate = onlineCaptains.firstOrNull { it !in attemptedCaptains }

        if (candidate != null) {
            // Create offer
            val offer = DispatchOffer(
                jobId = job.jobId!!,
                captainId = candidate,
                offerRank = job.attemptCount + 1
            )
            offerRepository.save(offer)

            job.status = JobStatus.OFFERED
            job.attemptCount += 1
            jobRepository.save(job)

            // Publish Offer Event
            publishDispatchEvent(
                "DispatchJobOffered",
                job,
                candidate,
                mapOf(
                    "offer_id" to offer.offerId.toString(),
                    "offer_rank" to offer.offerRank
                )
            )

            logger.info("Offered Job {} to Captain {}.", job.jobId, candidate)
        } else {
            // No candidate available, try again or fail
            job.attemptCount += 1
            jobRepository.save(job)
            // Trigger loop iteration immediately
            triggerNextOffer(job)
        }
    }

    fun respondToOffer(offerId: UUID, response: String, captainId: UUID): DispatchOffer {
        val offer = offerRepository.findById(offerId)
            .orElseThrow { NoSuchElementException("Dispatch offer not found for ID $offerId") }

        if (offer.captainId != captainId) {
            throw IllegalStateException("Offer does not belong to authenticated captain")
        }

        if (offer.response != null) {
            throw IllegalStateException("Offer already responded with ${offer.response}")
        }

        val savedOffer = try {
            offer.response = response
            offer.respondedAt = Instant.now()
            offerRepository.saveAndFlush(offer)
        } catch (e: ObjectOptimisticLockingFailureException) {
            val current = offerRepository.findById(offerId).get()
            throw IllegalStateException("This offer already resolved as ${current.response}. It may have timed out.")
        }

        val job = jobRepository.findById(offer.jobId).get()

        if (response == "ACCEPTED") {
            job.status = JobStatus.ACCEPTED
            jobRepository.save(job)

            updateOrderStatus(job.orderId, "ASSIGNED", offer.captainId, "Captain accepted dispatch offer")

            // Publish accepted event
            publishDispatchEvent("DispatchJobAccepted", job, offer.captainId, mapOf("offer_id" to offer.offerId.toString()))

            logger.info("Job {} ACCEPTED by Captain {}.", job.jobId, offer.captainId)
        } else {
            job.status = JobStatus.PENDING_ASSIGNMENT
            jobRepository.save(job)
            triggerNextOffer(job)
        }

        return savedOffer
    }

    fun markPickedUp(jobId: UUID, captainId: UUID, proofCode: String?): DispatchJob {
        val job = loadAcceptedJobForCaptain(jobId, captainId)
        if (proofCode.isNullOrBlank()) {
            throw IllegalArgumentException("Pickup proof code is required")
        }
        if (job.pickupOtp != null && job.pickupOtp != proofCode) {
            throw IllegalArgumentException("Invalid pickup verification OTP")
        }

        updateOrderStatus(job.orderId, "PICKED_UP", captainId, "Pickup proof verified by captain app")
        publishDispatchEvent("DispatchJobPickedUp", job, captainId, mapOf("proof_status" to "VERIFIED"))
        return jobRepository.save(job)
    }

    fun markDelivered(jobId: UUID, captainId: UUID, proofCode: String?): DispatchJob {
        val job = loadAcceptedJobForCaptain(jobId, captainId)
        if (proofCode.isNullOrBlank()) {
            throw IllegalArgumentException("Delivery proof code is required")
        }
        if (job.deliveryOtp != null && job.deliveryOtp != proofCode) {
            throw IllegalArgumentException("Invalid handover verification OTP")
        }

        updateOrderStatus(job.orderId, "DELIVERED", captainId, "Delivery proof verified by captain app")
        job.status = JobStatus.COMPLETED
        job.resolvedAt = Instant.now()
        val savedJob = jobRepository.save(job)
        publishDispatchEvent("DispatchJobDelivered", savedJob, captainId, mapOf("proof_status" to "VERIFIED"))
        return savedJob
    }

    // --- Expiry / Timeout Scheduler (Task 4) ---
    @Scheduled(fixedDelay = 5000)
    fun checkOfferTimeouts() {
        val activeJobs = jobRepository.findByStatus(JobStatus.OFFERED)
        for (job in activeJobs) {
            val pendingOffer = offerRepository.findByJobIdAndResponseIsNull(job.jobId!!)
            if (pendingOffer != null) {
                // If offer is older than 30 seconds, time it out!
                if (Instant.now().isAfter(pendingOffer.offeredAt.plusSeconds(30))) {
                    try {
                        pendingOffer.response = "TIMED_OUT"
                        pendingOffer.respondedAt = Instant.now()
                        offerRepository.saveAndFlush(pendingOffer)
                    } catch (e: ObjectOptimisticLockingFailureException) {
                        logger.info("Offer {} was resolved by the captain just before timeout; skipping reassignment.", pendingOffer.offerId)
                        continue
                    }

                    job.status = JobStatus.PENDING_ASSIGNMENT
                    jobRepository.save(job)

                    logger.info("Offer {} to Captain {} TIMED OUT. Retrying assignment.", pendingOffer.offerId, pendingOffer.captainId)
                    triggerNextOffer(job)
                }
            }
        }
    }

    private fun getProviderCoordinates(orderId: UUID): Pair<Double, Double>? {
        return try {
            val query = entityManager.createNativeQuery("""
                SELECT ST_X(CAST(p.geo_location AS geometry)) as lng, ST_Y(CAST(p.geo_location AS geometry)) as lat
                FROM orders.orders o
                JOIN providers.providers p ON o.provider_id = p.provider_id
                WHERE o.order_id = :orderId
            """)
            query.setParameter("orderId", orderId)
            val result = query.singleResult as Array<*>
            val lng = (result[0] as Number).toDouble()
            val lat = (result[1] as Number).toDouble()
            Pair(lng, lat)
        } catch (e: Exception) {
            logger.warn("Failed to fetch coordinates for order {}: {}", orderId, e.message, e)
            null
        }
    }

    private fun loadAcceptedJobForCaptain(jobId: UUID, captainId: UUID): DispatchJob {
        val job = jobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Dispatch job not found for ID $jobId") }
        if (job.status != JobStatus.ACCEPTED) {
            throw IllegalStateException("Dispatch job must be ACCEPTED before delivery progress can be recorded")
        }
        val offer = offerRepository.findByJobIdAndCaptainId(jobId, captainId)
            ?: throw IllegalStateException("Authenticated captain is not assigned to this dispatch job")
        if (offer.response != "ACCEPTED") {
            throw IllegalStateException("Authenticated captain has not accepted this dispatch job")
        }
        return job
    }

    private fun updateOrderStatus(orderId: UUID, status: String, captainId: UUID, note: String) {
        val headers = internalHeaders()
        headers.set("X-User-Id", captainId.toString())
        val entity = org.springframework.http.HttpEntity<Any>(headers)
        val encodedNote = URLEncoder.encode(note, StandardCharsets.UTF_8)
        restTemplate.exchange(
            "$orderServiceUrl/$orderId/status?status=$status&note=$encodedNote",
            org.springframework.http.HttpMethod.PUT,
            entity,
            Any::class.java
        )
    }

    private fun internalHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        if (gatewayTrustSecret.isNotBlank()) {
            headers.set("X-Internal-Gateway-Secret", gatewayTrustSecret)
        }
        return headers
    }

    private fun publishDispatchEvent(
        eventType: String,
        job: DispatchJob,
        captainId: UUID?,
        attributes: Map<String, Any?>
    ) {
        val eventId = UUID.randomUUID()
        val event = mutableMapOf<String, Any?>(
            "event_id" to eventId.toString(),
            "event_type" to eventType,
            "occurred_at" to Instant.now().toString(),
            "job_id" to job.jobId.toString(),
            "order_id" to job.orderId.toString(),
            "actor_id" to captainId?.toString(),
            "captain_id" to captainId?.toString(),
            "status" to job.status.name,
            "attempt_count" to job.attemptCount
        )
        event.putAll(attributes)
        
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "DISPATCH",
            aggregateId = job.jobId!!,
            eventType = eventType,
            eventPayload = event
        )
    }
}
