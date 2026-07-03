package com.pawsnearme.dispatchservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.dispatchservice.model.*
import com.pawsnearme.dispatchservice.repository.*
import jakarta.persistence.EntityManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
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
    @Value("\${ORDER_SERVICE_URL:http://localhost:8084}")
    private val orderServiceBaseUrl: String = "http://localhost:8084",
    private val restTemplate: RestTemplate = RestTemplate()
) {

    private val objectMapper = ObjectMapper()
    private val orderServiceUrl = "$orderServiceBaseUrl/api/v1/orders"

    companion object {
        private const val GEO_KEY = "captains:locations"
    }

    // --- Kafka Listener for Orders ---
    @KafkaListener(topics = ["orders.events"], groupId = "dispatch-service-group-v2")
    fun handleOrderStatusChanged(record: ConsumerRecord<String, String>) {
        val event: Map<String, Any> = try {
            objectMapper.readValue(record.value(), object : TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            println("WARNING: Failed to parse Kafka event: ${e.message}")
            return
        }
        val toStatus = event["toStatus"] as? String

        if (toStatus == "READY_FOR_PICKUP") {
            val orderIdStr = event["orderId"] as? String ?: return
            val orderId = UUID.fromString(orderIdStr)
            println("DispatchJob: Received READY_FOR_PICKUP for order $orderId. Starting dispatch...")
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
            attemptCount = 0
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
            println("DispatchJob: Failed to assign order ${job.orderId} after ${job.maxAttempts} attempts.")
            
            publishDispatchEvent("DispatchJobFailed", job, null, mapOf("reason" to "MAX_ATTEMPTS_EXHAUSTED"))
            return
        }

        // 1. Get Provider coordinates from DB
        val providerCoords = getProviderCoordinates(job.orderId)
        if (providerCoords == null) {
            println("DispatchJob: Coordinates for order ${job.orderId} not found. Failing job.")
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
            println("WARNING: Redis Geo lookup failed: ${e.message}")
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
            try {
                publishDispatchEvent(
                    "DispatchJobOffered",
                    job,
                    candidate,
                    mapOf(
                        "offer_id" to offer.offerId.toString(),
                        "offer_rank" to offer.offerRank
                    )
                )
            } catch (e: Exception) {
                println("WARNING: Failed to publish Kafka DispatchJobOffered event: ${e.message}")
            }

            println("DispatchJob: Offered Job ${job.jobId} to Captain $candidate.")
        } else {
            // No candidate available, try again or fail
            job.attemptCount += 1
            jobRepository.save(job)
            // Trigger loop iteration immediately
            triggerNextOffer(job)
        }
    }

    fun respondToOffer(offerId: UUID, response: String, captainId: UUID? = null): DispatchOffer {
        val offer = offerRepository.findById(offerId)
            .orElseThrow { NoSuchElementException("Dispatch offer not found for ID $offerId") }

        if (captainId != null && offer.captainId != captainId) {
            throw IllegalStateException("Offer does not belong to authenticated captain")
        }

        if (offer.response != null) {
            throw IllegalStateException("Offer already responded with ${offer.response}")
        }

        offer.response = response
        offer.respondedAt = Instant.now()
        val savedOffer = offerRepository.save(offer)

        val job = jobRepository.findById(offer.jobId).get()

        if (response == "ACCEPTED") {
            job.status = JobStatus.ACCEPTED
            jobRepository.save(job)

            updateOrderStatus(job.orderId, "ASSIGNED", offer.captainId, "Captain accepted dispatch offer")

            // Publish accepted event
            try {
                publishDispatchEvent("DispatchJobAccepted", job, offer.captainId, mapOf("offer_id" to offer.offerId.toString()))
            } catch (e: Exception) {
                println("WARNING: Failed to publish Kafka DispatchJobAccepted event: ${e.message}")
            }

            println("DispatchJob: Job ${job.jobId} ACCEPTED by Captain ${offer.captainId}.")
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

        updateOrderStatus(job.orderId, "PICKED_UP", captainId, "Pickup proof verified by captain app")
        publishDispatchEvent("DispatchJobPickedUp", job, captainId, mapOf("proof_status" to "VERIFIED"))
        return jobRepository.save(job)
    }

    fun markDelivered(jobId: UUID, captainId: UUID, proofCode: String?): DispatchJob {
        val job = loadAcceptedJobForCaptain(jobId, captainId)
        if (proofCode.isNullOrBlank()) {
            throw IllegalArgumentException("Delivery proof code is required")
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
        val activeJobs = jobRepository.findAll().filter { it.status == JobStatus.OFFERED }
        for (job in activeJobs) {
            val pendingOffer = offerRepository.findByJobIdAndResponseIsNull(job.jobId!!)
            if (pendingOffer != null) {
                // If offer is older than 30 seconds, time it out!
                if (Instant.now().isAfter(pendingOffer.offeredAt.plusSeconds(30))) {
                    pendingOffer.response = "TIMED_OUT"
                    pendingOffer.respondedAt = Instant.now()
                    offerRepository.save(pendingOffer)

                    job.status = JobStatus.PENDING_ASSIGNMENT
                    jobRepository.save(job)

                    println("DispatchJob: Offer ${pendingOffer.offerId} to Captain ${pendingOffer.captainId} TIMED OUT. Retrying assignment.")
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
            println("WARNING: Failed to fetch coordinates for order $orderId: ${e.message}")
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
        val headers = org.springframework.http.HttpHeaders()
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

    private fun publishDispatchEvent(
        eventType: String,
        job: DispatchJob,
        captainId: UUID?,
        attributes: Map<String, Any?>
    ) {
        val event = mutableMapOf<String, Any?>(
            "event_id" to UUID.randomUUID().toString(),
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
        try {
            val jsonString = objectMapper.writeValueAsString(event)
            kafkaTemplate.send("dispatch.events", job.jobId.toString(), jsonString)
        } catch (e: Exception) {
            println("WARNING: Failed to publish $eventType event: ${e.message}")
        }
    }
}
