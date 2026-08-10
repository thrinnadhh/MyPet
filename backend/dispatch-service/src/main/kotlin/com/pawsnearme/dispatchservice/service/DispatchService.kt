package com.pawsnearme.dispatchservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.common.module.OrderModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.dispatchservice.model.*
import com.pawsnearme.dispatchservice.module.RemoteOrderModuleApi
import com.pawsnearme.dispatchservice.repository.*
import jakarta.persistence.EntityManager
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point as RedisPoint
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DispatchService @Autowired constructor(
    private val jobRepository: DispatchJobRepository,
    private val offerRepository: DispatchOfferRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val entityManager: EntityManager,
    private val outboxService: OutboxService,
    private val idempotencyService: IdempotencyService,
    private val orderModule: OrderModuleApi
) {
    /** Compatibility constructor retained for focused legacy tests and rollback tooling. */
    constructor(
        jobRepository: DispatchJobRepository,
        offerRepository: DispatchOfferRepository,
        redisTemplate: StringRedisTemplate,
        kafkaTemplate: KafkaTemplate<String, Any>,
        entityManager: EntityManager,
        outboxService: OutboxService,
        idempotencyService: IdempotencyService,
        orderServiceBaseUrl: String = "http://localhost:8084",
        gatewayTrustSecret: String = "",
        restTemplate: RestOperations = RestTemplate()
    ) : this(
        jobRepository,
        offerRepository,
        redisTemplate,
        kafkaTemplate,
        entityManager,
        outboxService,
        idempotencyService,
        RemoteOrderModuleApi(restTemplate, orderServiceBaseUrl, gatewayTrustSecret)
    )

    private val objectMapper = ObjectMapper()

    companion object {
        private val logger = LoggerFactory.getLogger(DispatchService::class.java)
        private const val GEO_KEY = "captains:locations"
        private const val ONLINE_KEY = "captains:online"
        private const val LOCATION_FRESH_PREFIX = "captains:location:fresh:"
        private const val ORDER_EVENT_CONSUMER_SCOPE = "dispatch-orders"
    }

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

        // Dispatch is owned by exactly one canonical transition. A READY status
        // produced by any other source must never start captain search.
        if (event["fromStatus"] as? String != "PREPARING" || event["toStatus"] as? String != "READY_FOR_PICKUP") {
            return
        }

        val eventIdStr = event["eventId"] as? String ?: event["event_id"] as? String ?: return
        val eventId = try {
            UUID.fromString(eventIdStr)
        } catch (e: Exception) {
            return
        }

        if (!idempotencyService.checkAndRecord(ORDER_EVENT_CONSUMER_SCOPE, eventId)) {
            logger.info("Duplicate dispatch order event ignored: {}", eventId)
            return
        }

        val orderIdStr = event["orderId"] as? String ?: return
        val orderId = UUID.fromString(orderIdStr)
        logger.info("Received PREPARING -> READY_FOR_PICKUP for order {}. Starting dispatch...", orderId)
        startDispatchProcess(orderId)
    }

    fun startDispatchProcess(orderId: UUID): DispatchJob {
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
        var currentJob = job

        while (true) {
            if (currentJob.attemptCount >= currentJob.maxAttempts) {
                currentJob.status = JobStatus.FAILED
                currentJob.resolvedAt = Instant.now()
                jobRepository.save(currentJob)
                logger.error(
                    "Failed to assign order {} after {} attempts — MAX_ATTEMPTS_EXHAUSTED.",
                    currentJob.orderId,
                    currentJob.maxAttempts
                )
                publishDispatchEvent(
                    "DispatchJobFailed",
                    currentJob,
                    null,
                    mapOf("reason" to "MAX_ATTEMPTS_EXHAUSTED")
                )
                return
            }

            val providerCoords = getProviderCoordinates(currentJob.orderId)
            if (providerCoords == null) {
                logger.error("Coordinates for order {} not found. Failing job.", currentJob.orderId)
                currentJob.status = JobStatus.FAILED
                currentJob.resolvedAt = Instant.now()
                jobRepository.save(currentJob)
                publishDispatchEvent(
                    "DispatchJobFailed",
                    currentJob,
                    null,
                    mapOf("reason" to "PROVIDER_COORDINATES_UNAVAILABLE")
                )
                return
            }
            val (lng, lat) = providerCoords

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

            val geoCaptains = results?.content?.mapNotNull { result ->
                runCatching { UUID.fromString(result.content.name) }.getOrNull()
            } ?: emptyList()
            val existingOffers = offerRepository.findByJobId(requireNotNull(currentJob.jobId))
            val attemptedCaptains = existingOffers.map { it.captainId }.toSet()
            val candidate = geoCaptains.firstOrNull { captainId ->
                captainId !in attemptedCaptains && isCaptainEligible(captainId, requireNotNull(currentJob.jobId))
            }

            if (candidate != null) {
                val offer = DispatchOffer(
                    jobId = currentJob.jobId!!,
                    captainId = candidate,
                    offerRank = currentJob.attemptCount + 1
                )
                offerRepository.save(offer)
                currentJob.status = JobStatus.OFFERED
                currentJob.attemptCount += 1
                jobRepository.save(currentJob)
                publishDispatchEvent(
                    "DispatchJobOffered",
                    currentJob,
                    candidate,
                    mapOf("offer_id" to offer.offerId.toString(), "offer_rank" to offer.offerRank)
                )
                logger.info("Offered Job {} to eligible Captain {}.", currentJob.jobId, candidate)
                return
            }

            currentJob.attemptCount += 1
            currentJob = jobRepository.save(currentJob)
            logger.debug(
                "No eligible captain for job {} on attempt {}. Retrying in loop.",
                currentJob.jobId,
                currentJob.attemptCount
            )
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
        if (response == "ACCEPTED" && !isCaptainEligible(captainId, offer.jobId)) {
            throw IllegalStateException("Captain is no longer eligible to accept this delivery offer")
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
            publishDispatchEvent(
                "DispatchJobAccepted",
                job,
                offer.captainId,
                mapOf("offer_id" to offer.offerId.toString())
            )
            logger.info("Job {} ACCEPTED by Captain {}.", job.jobId, offer.captainId)
        } else {
            job.status = JobStatus.PENDING_ASSIGNMENT
            jobRepository.save(job)
            triggerNextOffer(job)
        }
        return savedOffer
    }

    fun markPickedUp(jobId: UUID, captainId: UUID, proofCode: String?): DispatchJob {
        val job = loadAssignedJobForCaptain(jobId, captainId, setOf(JobStatus.ACCEPTED))
        if (proofCode.isNullOrBlank()) throw IllegalArgumentException("Pickup proof code is required")
        if (job.pickupOtp != null && job.pickupOtp != proofCode) {
            throw IllegalArgumentException("Invalid pickup verification OTP")
        }

        updateOrderStatus(job.orderId, "PICKED_UP", captainId, "Pickup proof verified by captain app")
        job.status = JobStatus.PICKED_UP
        val savedJob = jobRepository.save(job)
        publishDispatchEvent("DispatchJobPickedUp", savedJob, captainId, mapOf("proof_status" to "VERIFIED"))
        return savedJob
    }

    fun markDelivered(jobId: UUID, captainId: UUID, proofCode: String?): DispatchJob {
        val job = loadAssignedJobForCaptain(jobId, captainId, setOf(JobStatus.PICKED_UP))
        if (proofCode.isNullOrBlank()) throw IllegalArgumentException("Delivery proof code is required")
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

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "dispatch_checkOfferTimeouts", lockAtMostFor = "PT25S", lockAtLeastFor = "PT5S")
    fun checkOfferTimeouts() {
        val activeJobs = jobRepository.findByStatus(JobStatus.OFFERED)
        for (job in activeJobs) {
            val pendingOffer = offerRepository.findByJobIdAndResponseIsNull(job.jobId!!)
            if (pendingOffer != null && Instant.now().isAfter(pendingOffer.offeredAt.plusSeconds(30))) {
                try {
                    pendingOffer.response = "TIMED_OUT"
                    pendingOffer.respondedAt = Instant.now()
                    offerRepository.saveAndFlush(pendingOffer)
                } catch (e: ObjectOptimisticLockingFailureException) {
                    logger.info(
                        "Offer {} was resolved by the captain just before timeout; skipping reassignment.",
                        pendingOffer.offerId
                    )
                    continue
                }

                job.status = JobStatus.PENDING_ASSIGNMENT
                jobRepository.save(job)
                logger.info(
                    "Offer {} to Captain {} TIMED OUT. Retrying assignment.",
                    pendingOffer.offerId,
                    pendingOffer.captainId
                )
                triggerNextOffer(job)
            }
        }
    }

    private fun isCaptainEligible(captainId: UUID, currentJobId: UUID): Boolean {
        if (!isCaptainApproved(captainId)) return false

        val online = redisTemplate.opsForSet().isMember(ONLINE_KEY, captainId.toString()) == true
        val locationFresh = redisTemplate.hasKey("$LOCATION_FRESH_PREFIX$captainId") == true
        if (!online || !locationFresh) return false

        val anotherPendingOffer = offerRepository.findByCaptainIdAndResponseIsNull(captainId)
            .any { it.jobId != currentJobId }
        if (anotherPendingOffer) return false

        val activeAssignment = offerRepository
            .findByCaptainIdAndResponseOrderByRespondedAtDesc(captainId, "ACCEPTED")
            .any { acceptedOffer ->
                val assignedJob = jobRepository.findById(acceptedOffer.jobId).orElse(null)
                assignedJob?.status in setOf(JobStatus.ACCEPTED, JobStatus.PICKED_UP)
            }
        return !activeAssignment
    }

    private fun isCaptainApproved(captainId: UUID): Boolean = try {
        val query = entityManager.createNativeQuery(
            """
                SELECT COUNT(*)
                FROM captains.captain_profiles
                WHERE captain_id = :captainId
                  AND status = 'ACTIVE'
            """.trimIndent()
        )
        query.setParameter("captainId", captainId)
        (query.singleResult as Number).toLong() == 1L
    } catch (error: Exception) {
        logger.warn("Captain approval lookup failed for {}: {}", captainId, error.message)
        false
    }

    private fun getProviderCoordinates(orderId: UUID): Pair<Double, Double>? = try {
        val query = entityManager.createNativeQuery(
            """
                SELECT ST_X(CAST(p.geo_location AS geometry)) as lng, ST_Y(CAST(p.geo_location AS geometry)) as lat
                FROM orders.orders o
                JOIN providers.providers p ON o.provider_id = p.provider_id
                WHERE o.order_id = :orderId
            """.trimIndent()
        )
        query.setParameter("orderId", orderId)
        val result = query.singleResult as Array<*>
        Pair((result[0] as Number).toDouble(), (result[1] as Number).toDouble())
    } catch (e: Exception) {
        logger.warn("Failed to fetch coordinates for order {}: {}", orderId, e.message, e)
        null
    }

    private fun loadAssignedJobForCaptain(
        jobId: UUID,
        captainId: UUID,
        allowedStatuses: Set<JobStatus>
    ): DispatchJob {
        val job = jobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Dispatch job not found for ID $jobId") }
        if (job.status !in allowedStatuses) {
            throw IllegalStateException(
                "Dispatch job in state ${job.status} cannot perform this delivery transition"
            )
        }
        val offer = offerRepository.findByJobIdAndCaptainId(jobId, captainId)
            ?: throw IllegalStateException("Authenticated captain is not assigned to this dispatch job")
        if (offer.response != "ACCEPTED") {
            throw IllegalStateException("Authenticated captain has not accepted this dispatch job")
        }
        return job
    }

    private fun updateOrderStatus(orderId: UUID, status: String, captainId: UUID, note: String) {
        orderModule.updateStatus(orderId, status, captainId, note)
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
