package com.pawsnearme.dispatchservice.service

import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.client.RestOperations
import java.util.Optional
import java.util.UUID

class DispatchRejectionFlowTests {
    private val jobRepository: DispatchJobRepository = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val entityManager: EntityManager = mock()
    private val outboxService: OutboxService = mock()
    private val idempotencyService: IdempotencyService = mock()
    private val restTemplate: RestOperations = mock()

    private fun service() = DispatchService(
        jobRepository,
        offerRepository,
        redisTemplate,
        kafkaTemplate,
        entityManager,
        outboxService,
        idempotencyService,
        restTemplate = restTemplate,
    )

    @Test
    fun `first captain rejection returns job to reassignment flow`() {
        val realService = service()
        val dispatchService = spy(realService)
        val jobId = UUID.randomUUID()
        val offerId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(
            jobId = jobId,
            orderId = UUID.randomUUID(),
            status = JobStatus.OFFERED,
            attemptCount = 1,
            maxAttempts = 3,
        )
        val offer = DispatchOffer(
            offerId = offerId,
            jobId = jobId,
            captainId = captainId,
            offerRank = 1,
        )
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))
        whenever(offerRepository.saveAndFlush(offer)).thenReturn(offer)
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(jobRepository.save(job)).thenReturn(job)
        doNothing().whenever(dispatchService).triggerNextOffer(job)

        val result = dispatchService.respondToOffer(offerId, "REJECTED", captainId)

        assertEquals("REJECTED", result.response)
        assertEquals(JobStatus.PENDING_ASSIGNMENT, job.status)
        verify(dispatchService).triggerNextOffer(job)
    }

    @Test
    fun `third captain rejection exhausts dispatch and records failure`() {
        val dispatchService = service()
        val jobId = UUID.randomUUID()
        val offerId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(
            jobId = jobId,
            orderId = UUID.randomUUID(),
            status = JobStatus.OFFERED,
            attemptCount = 3,
            maxAttempts = 3,
        )
        val offer = DispatchOffer(
            offerId = offerId,
            jobId = jobId,
            captainId = captainId,
            offerRank = 3,
        )
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))
        whenever(offerRepository.saveAndFlush(offer)).thenReturn(offer)
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(jobRepository.save(any())).thenAnswer { it.getArgument<DispatchJob>(0) }

        val result = dispatchService.respondToOffer(offerId, "REJECTED", captainId)

        assertEquals("REJECTED", result.response)
        assertEquals(JobStatus.FAILED, job.status)
        assertNotNull(job.resolvedAt)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("DISPATCH"),
            aggregateId = eq(jobId),
            eventType = eq("DispatchJobFailed"),
            eventPayload = any(),
        )
    }
}
