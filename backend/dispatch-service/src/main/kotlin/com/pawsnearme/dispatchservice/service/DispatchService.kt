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
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DispatchService(
    private val jobRepository: DispatchJobRepository,
    private val offerRepository: DispatchOfferRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val entityManager: EntityManager
) {

    private val restTemplate = RestTemplate()
    private val orderServiceUrl = "http://localhost:8084/api/v1/orders"

    companion object {
        private const val GEO_KEY = "captains:locations"
    }

    // --- Kafka Listener for Orders ---
    @KafkaListener(topics = ["orders.events"], groupId = "dispatch-service-group-v2")
    fun handleOrderStatusChanged(record: ConsumerRecord<String, String>) {
        val event: Map<String, Any> = try {
            ObjectMapper().readValue(record.value(), object : TypeReference<Map<String, Any>>() {})
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
            
            // Notify order service that dispatch failed
            try {
                restTemplate.put("$orderServiceUrl/${job.orderId}/status?status=CANCELLED&note=No Captains available", null)
            } catch (e: Exception) {
                println("WARNING: Failed to cancel order via REST: ${e.message}")
            }
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
                val event = mapOf(
                    "event_type" to "DispatchJobOffered",
                    "jobId" to job.jobId.toString(),
                    "offerId" to offer.offerId.toString(),
                    "orderId" to job.orderId.toString(),
                    "captainId" to candidate.toString(),
                    "timestamp" to Instant.now().toString()
                )
                val jsonString = ObjectMapper().writeValueAsString(event)
                kafkaTemplate.send("dispatch.events", job.jobId.toString(), jsonString)
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

    fun respondToOffer(offerId: UUID, response: String): DispatchOffer {
        val offer = offerRepository.findById(offerId)
            .orElseThrow { NoSuchElementException("Dispatch offer not found for ID $offerId") }

        if (offer.response != null) {
            throw IllegalStateException("Offer already responded with ${offer.response}")
        }

        offer.response = response
        offer.respondedAt = Instant.now()
        val savedOffer = offerRepository.save(offer)

        val job = jobRepository.findById(offer.jobId).get()

        if (response == "ACCEPTED") {
            job.status = JobStatus.ACCEPTED
            job.resolvedAt = Instant.now()
            jobRepository.save(job)

            // Update order service status to ASSIGNED with captainId
            try {
                val headers = org.springframework.http.HttpHeaders()
                headers.set("X-User-Id", offer.captainId.toString())
                val entity = org.springframework.http.HttpEntity<Any>(headers)
                restTemplate.exchange(
                    "$orderServiceUrl/${job.orderId}/status?status=ASSIGNED",
                    org.springframework.http.HttpMethod.PUT,
                    entity,
                    Any::class.java
                )
            } catch (e: Exception) {
                println("WARNING: Failed to assign order via REST: ${e.message}")
            }

            // Publish accepted event
            try {
                val event = mapOf(
                    "event_type" to "DispatchJobAccepted",
                    "jobId" to job.jobId.toString(),
                    "orderId" to job.orderId.toString(),
                    "captainId" to offer.captainId.toString(),
                    "timestamp" to Instant.now().toString()
                )
                val jsonString = ObjectMapper().writeValueAsString(event)
                kafkaTemplate.send("dispatch.events", job.jobId.toString(), jsonString)
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
}
