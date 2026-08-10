package com.pawsnearme.dispatchservice.service

import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.geo.GeoResults
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.core.GeoOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.client.RestOperations
import java.util.UUID

class DispatchNoCaptainTests {
    private val jobRepository: DispatchJobRepository = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val geoOperations: GeoOperations<String, String> = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val entityManager: EntityManager = mock()
    private val coordinateQuery: Query = mock()
    private val outboxService: OutboxService = mock()
    private val idempotencyService: IdempotencyService = mock()
    private val restTemplate: RestOperations = mock()

    @Test
    fun `no captains online exhausts assignment without creating an offer`() {
        val service = DispatchService(
            jobRepository,
            offerRepository,
            redisTemplate,
            kafkaTemplate,
            entityManager,
            outboxService,
            idempotencyService,
            restTemplate = restTemplate,
        )
        val jobId = UUID.randomUUID()
        val job = DispatchJob(
            jobId = jobId,
            orderId = UUID.randomUUID(),
            status = JobStatus.PENDING_ASSIGNMENT,
            attemptCount = 0,
            maxAttempts = 3,
        )
        whenever(entityManager.createNativeQuery(argThat<String> { contains("providers.providers") }))
            .thenReturn(coordinateQuery)
        whenever(coordinateQuery.setParameter(eq("orderId"), eq(job.orderId))).thenReturn(coordinateQuery)
        whenever(coordinateQuery.singleResult).thenReturn(arrayOf<Any>(79.42, 13.63))
        whenever(redisTemplate.opsForGeo()).thenReturn(geoOperations)
        whenever(
            geoOperations.search(
                eq("captains:locations"),
                any(),
                any(),
                any(),
            )
        ).thenReturn(GeoResults<RedisGeoCommands.GeoLocation<String>>(emptyList()))
        whenever(offerRepository.findByJobId(jobId)).thenReturn(emptyList())
        whenever(jobRepository.save(any())).thenAnswer { it.getArgument<DispatchJob>(0) }

        service.triggerNextOffer(job)

        assertEquals(3, job.attemptCount)
        assertEquals(JobStatus.FAILED, job.status)
        assertNotNull(job.resolvedAt)
        verify(offerRepository, org.mockito.kotlin.never()).save(any())
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("DISPATCH"),
            aggregateId = eq(jobId),
            eventType = eq("DispatchJobFailed"),
            eventPayload = any(),
        )
    }
}
