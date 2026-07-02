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

class DispatchServiceTests {

    private val jobRepository: DispatchJobRepository = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val entityManager: EntityManager = mock()

    private val service = DispatchService(
        jobRepository, offerRepository, redisTemplate, kafkaTemplate, entityManager
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

        assertThrows<NoSuchElementException> { service.respondToOffer(offerId, "ACCEPTED") }
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

        val ex = assertThrows<IllegalStateException> { service.respondToOffer(offerId, "REJECTED") }
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
        verify(kafkaTemplate).send(
            eq("dispatch.events"),
            eq(jobId.toString()),
            argThat<String> { contains("DispatchJobPickedUp") }
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
        verify(kafkaTemplate).send(
            eq("dispatch.events"),
            eq(jobId.toString()),
            argThat<String> { contains("DispatchJobDelivered") }
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
        verify(kafkaTemplate).send(
            eq("dispatch.events"),
            eq(jobId.toString()),
            argThat<String> { contains("DispatchJobFailed") }
        )
    }
}
