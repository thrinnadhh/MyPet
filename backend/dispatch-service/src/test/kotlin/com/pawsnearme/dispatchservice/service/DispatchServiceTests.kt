package com.pawsnearme.dispatchservice.service

import com.pawsnearme.dispatchservice.model.*
import com.pawsnearme.dispatchservice.repository.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.springframework.orm.ObjectOptimisticLockingFailureException

class DispatchServiceTests {

    private val jobRepository: DispatchJobRepository = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val entityManager: EntityManager = mock()

    private val outboxService: com.pawsnearme.common.outbox.OutboxService = mock()
    private val idempotencyService: com.pawsnearme.common.idempotency.IdempotencyService = mock()
    private val restTemplate: org.springframework.web.client.RestOperations = mock()

    private val service = DispatchService(
        jobRepository, offerRepository, redisTemplate, kafkaTemplate, entityManager,
        outboxService, idempotencyService, restTemplate = restTemplate
    )

    // ── startDispatchProcess ──────────────────────────────────────────────────

    @Test
    fun `startDispatchProcess - duplicate job - returns existing without saving again`() {
        val orderId = UUID.randomUUID()
        val existingJob = DispatchJob(orderId = orderId, status = JobStatus.ACCEPTED)
            .also { it.jobId = UUID.randomUUID() }

        whenever(jobRepository.findByOrderId(orderId)).thenReturn(existingJob)

        val result = service.startDispatchProcess(orderId)

        assertEquals(existingJob.jobId, result.jobId)
        verify(jobRepository, never()).save(any()) // No second save
    }

    // ── respondToOffer ────────────────────────────────────────────────────────

    @Test
    fun `respondToOffer - offer not found - throws NoSuchElementException`() {
        val offerId = UUID.randomUUID()
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> { service.respondToOffer(offerId, "ACCEPTED", UUID.randomUUID()) }
    }

    @Test
    fun `respondToOffer - already responded - throws IllegalStateException`() {
        val offerId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val offer = DispatchOffer(
            jobId = jobId,
            captainId = UUID.randomUUID(),
            response = "ACCEPTED", // already set
            offeredAt = Instant.now(),
            offerRank = 1
        )

        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))

        val ex = assertThrows<IllegalStateException> { service.respondToOffer(offerId, "REJECTED", offer.captainId) }
        assertTrue(ex.message!!.contains("already responded"))
    }

    @Test
    fun `respondToOffer - wrong captain - rejects authenticated mismatch`() {
        val offerId = UUID.randomUUID()
        val offer = DispatchOffer(
            jobId = UUID.randomUUID(),
            captainId = UUID.randomUUID(),
            offeredAt = Instant.now(),
            offerRank = 1
        )

        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))

        val ex = assertThrows<IllegalStateException> {
            service.respondToOffer(offerId, "ACCEPTED", UUID.randomUUID())
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `markPickedUp - accepted captain - updates order pickup status`() {
        val restTemplate: RestTemplate = mock()
        val serviceWithRest = DispatchService(
            jobRepository,
            offerRepository,
            redisTemplate,
            kafkaTemplate,
            entityManager,
            outboxService,
            idempotencyService,
            restTemplate = restTemplate
        )
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.ACCEPTED)
            .also { it.jobId = jobId }
        val offer = DispatchOffer(jobId = jobId, captainId = captainId, response = "ACCEPTED", offerRank = 1)

        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(offerRepository.findByJobIdAndCaptainId(jobId, captainId)).thenReturn(offer)
        whenever(jobRepository.save(job)).thenReturn(job)
        whenever(restTemplate.exchange(any<String>(), eq(HttpMethod.PUT), any(), eq(Any::class.java)))
            .thenReturn(ResponseEntity.ok<Any>(mapOf("status" to "PICKED_UP")))

        val result = serviceWithRest.markPickedUp(jobId, captainId, "1234")

        assertEquals(jobId, result.jobId)
        verify(restTemplate).exchange(
            argThat<String> { contains("status=PICKED_UP") },
            eq(HttpMethod.PUT),
            any(),
            eq(Any::class.java)
        )
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("DISPATCH"),
            aggregateId = eq(jobId),
            eventType = eq("DispatchJobPickedUp"),
            eventPayload = any()
        )
    }

    @Test
    fun `markDelivered - accepted captain - completes dispatch job`() {
        val restTemplate: RestTemplate = mock()
        val serviceWithRest = DispatchService(
            jobRepository,
            offerRepository,
            redisTemplate,
            kafkaTemplate,
            entityManager,
            outboxService,
            idempotencyService,
            restTemplate = restTemplate
        )
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.ACCEPTED)
            .also { it.jobId = jobId }
        val offer = DispatchOffer(jobId = jobId, captainId = captainId, response = "ACCEPTED", offerRank = 1)

        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(offerRepository.findByJobIdAndCaptainId(jobId, captainId)).thenReturn(offer)
        whenever(jobRepository.save(job)).thenReturn(job)
        whenever(restTemplate.exchange(any<String>(), eq(HttpMethod.PUT), any(), eq(Any::class.java)))
            .thenReturn(ResponseEntity.ok<Any>(mapOf("status" to "DELIVERED")))

        val result = serviceWithRest.markDelivered(jobId, captainId, "5678")

        assertEquals(JobStatus.COMPLETED, result.status)
        assertNotNull(result.resolvedAt)
        verify(restTemplate).exchange(
            argThat<String> { contains("status=DELIVERED") },
            eq(HttpMethod.PUT),
            any(),
            eq(Any::class.java)
        )
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("DISPATCH"),
            aggregateId = eq(jobId),
            eventType = eq("DispatchJobDelivered"),
            eventPayload = any()
        )
    }

    // ── triggerNextOffer — max attempts exceeded ───────────────────────────────

    @Test
    fun `triggerNextOffer - max attempts reached - marks job as FAILED and emits audit event`() {
        val jobId = UUID.randomUUID()
        val job = DispatchJob(
            orderId = UUID.randomUUID(),
            status = JobStatus.PENDING_ASSIGNMENT,
            attemptCount = 3,
            maxAttempts = 3
        ).also { it.jobId = jobId }

        whenever(jobRepository.save(any())).thenReturn(job)

        service.triggerNextOffer(job)

        assertEquals(JobStatus.FAILED, job.status)
        verify(jobRepository).save(job)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("DISPATCH"),
            aggregateId = eq(jobId),
            eventType = eq("DispatchJobFailed"),
            eventPayload = any()
        )
    }

    // ── Sprint 19: Optimistic Locking & Race Condition Tests ────────────────

    @Test
    fun `respondToOffer - optimistic locking conflict - throws clean IllegalStateException`() {
        val offerId = UUID.randomUUID()
        val offer = DispatchOffer(
            offerId = offerId,
            jobId = UUID.randomUUID(),
            captainId = UUID.randomUUID(),
            offeredAt = Instant.now(),
            offerRank = 1
        )

        // Simulate concurrent thread has already resolved this offer
        val resolvedOffer = DispatchOffer(
            offerId = offerId,
            jobId = offer.jobId,
            captainId = offer.captainId,
            offeredAt = offer.offeredAt,
            offerRank = offer.offerRank,
            response = "TIMED_OUT"
        )

        // First call returns unresolved offer, second call (inside catch block) returns resolved offer
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer), Optional.of(resolvedOffer))
        
        // Simulate concurrent modification where saveAndFlush throws
        whenever(offerRepository.saveAndFlush(any())).thenThrow(
            ObjectOptimisticLockingFailureException(DispatchOffer::class.java, offerId)
        )

        val ex = assertThrows<IllegalStateException> {
            service.respondToOffer(offerId, "ACCEPTED", offer.captainId)
        }
        assertTrue(ex.message!!.contains("already resolved as TIMED_OUT"))
    }

    @Test
    fun `checkOfferTimeouts - optimistic locking conflict - skips timeout and reassignment`() {
        val jobId = UUID.randomUUID()
        val job = DispatchJob(
            orderId = UUID.randomUUID(),
            status = JobStatus.OFFERED
        ).also { it.jobId = jobId }

        val offer = DispatchOffer(
            offerId = UUID.randomUUID(),
            jobId = jobId,
            captainId = UUID.randomUUID(),
            offeredAt = Instant.now().minusSeconds(60), // Expired
            offerRank = 1
        )

        whenever(jobRepository.findByStatus(JobStatus.OFFERED)).thenReturn(listOf(job))
        whenever(offerRepository.findByJobIdAndResponseIsNull(jobId)).thenReturn(offer)

        // Simulate another thread resolved the offer just before timeout check
        whenever(offerRepository.saveAndFlush(any())).thenThrow(
            ObjectOptimisticLockingFailureException(DispatchOffer::class.java, offer.offerId)
        )

        service.checkOfferTimeouts()

        // Job status should remain OFFERED, and no reassignment/timeout transition should occur
        assertEquals(JobStatus.OFFERED, job.status)
        verify(jobRepository, never()).save(job)
    }

    @Test
    fun `respondToOffer and timeout race - deterministic simulation`() {
        val jobId = UUID.randomUUID()
        val offerId = UUID.randomUUID()
        val captainId = UUID.randomUUID()

        val job = DispatchJob(
            orderId = UUID.randomUUID(),
            status = JobStatus.OFFERED
        ).also { it.jobId = jobId }

        val offer = DispatchOffer(
            offerId = offerId,
            jobId = jobId,
            captainId = captainId,
            offeredAt = Instant.now().minusSeconds(60),
            offerRank = 1
        )

        // Set up mock repository interactions
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(jobRepository.findByStatus(JobStatus.OFFERED)).thenReturn(listOf(job))
        whenever(offerRepository.findByJobIdAndResponseIsNull(jobId)).thenReturn(offer)
        whenever(restTemplate.exchange(any<String>(), eq(HttpMethod.PUT), any(), eq(Any::class.java)))
            .thenReturn(ResponseEntity.ok(Any()))

        // The first call (captain respondToOffer) succeeds, the second call (poller timeout check) fails
        var callCount = 0
        whenever(offerRepository.saveAndFlush(any())).thenAnswer {
            callCount++
            if (callCount == 1) {
                val arg = it.arguments[0] as DispatchOffer
                arg.response = "ACCEPTED"
                arg
            } else {
                throw ObjectOptimisticLockingFailureException(DispatchOffer::class.java, offerId)
            }
        }

        // Captain response wins the race
        val result = service.respondToOffer(offerId, "ACCEPTED", captainId)
        assertEquals("ACCEPTED", result.response)
        assertEquals(JobStatus.ACCEPTED, job.status)

        // Timeout check runs concurrently but fails the write race
        service.checkOfferTimeouts()

        // Assert job remains ACCEPTED by the captain, and is not timed out/reassigned
        assertEquals(JobStatus.ACCEPTED, job.status)
    }
}
