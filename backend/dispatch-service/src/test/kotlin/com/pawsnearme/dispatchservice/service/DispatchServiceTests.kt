package com.pawsnearme.dispatchservice.service

import com.pawsnearme.dispatchservice.model.*
import com.pawsnearme.dispatchservice.repository.*
import jakarta.persistence.EntityManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID

class DispatchServiceTests {

    private val jobRepository: DispatchJobRepository = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val redisSetOperations: SetOperations<String, String> = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val entityManager: EntityManager = mock()
    private val outboxService: com.pawsnearme.common.outbox.OutboxService = mock()
    private val idempotencyService: com.pawsnearme.common.idempotency.IdempotencyService = mock()
    private val restTemplate: org.springframework.web.client.RestOperations = mock()

    private val service = DispatchService(
        jobRepository, offerRepository, redisTemplate, kafkaTemplate, entityManager,
        outboxService, idempotencyService, restTemplate = restTemplate
    )

    @Test
    fun `order event idempotency is scoped to dispatch consumer`() {
        val eventId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        whenever(idempotencyService.checkAndRecord("dispatch-orders", eventId)).thenReturn(false)
        val record = ConsumerRecord(
            "orders.events",
            0,
            0L,
            orderId.toString(),
            """{"eventId":"$eventId","fromStatus":"PREPARING","toStatus":"READY_FOR_PICKUP","orderId":"$orderId"}"""
        )

        service.handleOrderStatusChanged(record)

        verify(idempotencyService).checkAndRecord("dispatch-orders", eventId)
        verify(idempotencyService, never()).checkAndRecord(eventId)
        verify(jobRepository, never()).findByOrderId(any())
    }

    @Test
    fun `READY_FOR_PICKUP without PREPARING transition never starts dispatch`() {
        val eventId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val record = ConsumerRecord(
            "orders.events",
            0,
            0L,
            orderId.toString(),
            """{"eventId":"$eventId","fromStatus":"ACCEPTED","toStatus":"READY_FOR_PICKUP","orderId":"$orderId"}"""
        )

        service.handleOrderStatusChanged(record)

        verify(idempotencyService, never()).checkAndRecord(any<String>(), any())
        verify(jobRepository, never()).findByOrderId(any())
    }

    @Test
    fun `startDispatchProcess - duplicate job - returns existing without saving again`() {
        val orderId = UUID.randomUUID()
        val existingJob = DispatchJob(orderId = orderId, status = JobStatus.ACCEPTED)
            .also { it.jobId = UUID.randomUUID() }
        whenever(jobRepository.findByOrderId(orderId)).thenReturn(existingJob)

        val result = service.startDispatchProcess(orderId)

        assertEquals(existingJob.jobId, result.jobId)
        verify(jobRepository, never()).save(any())
    }

    @Test
    fun `respondToOffer - offer not found - throws NoSuchElementException`() {
        val offerId = UUID.randomUUID()
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.empty())
        assertThrows<NoSuchElementException> { service.respondToOffer(offerId, "ACCEPTED", UUID.randomUUID()) }
    }

    @Test
    fun `respondToOffer - already responded - throws IllegalStateException`() {
        val offerId = UUID.randomUUID()
        val offer = DispatchOffer(
            jobId = UUID.randomUUID(),
            captainId = UUID.randomUUID(),
            response = "ACCEPTED",
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
    fun `captain must remain online and location-fresh when accepting offer`() {
        val offerId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val offer = DispatchOffer(offerId = offerId, jobId = jobId, captainId = captainId, offerRank = 1)
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))
        whenever(redisTemplate.opsForSet()).thenReturn(redisSetOperations)
        whenever(redisSetOperations.isMember("captains:online", captainId.toString())).thenReturn(true)
        whenever(redisTemplate.hasKey("captains:location:fresh:$captainId")).thenReturn(false)

        val error = assertThrows<IllegalStateException> {
            service.respondToOffer(offerId, "ACCEPTED", captainId)
        }

        assertTrue(error.message!!.contains("no longer eligible"))
        verify(offerRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `captain with another active assignment cannot accept second delivery`() {
        val offerId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val activeJobId = UUID.randomUUID()
        val offer = DispatchOffer(offerId = offerId, jobId = jobId, captainId = captainId, offerRank = 1)
        val acceptedOffer = DispatchOffer(jobId = activeJobId, captainId = captainId, response = "ACCEPTED", offerRank = 1)
        val activeJob = DispatchJob(jobId = activeJobId, orderId = UUID.randomUUID(), status = JobStatus.PICKED_UP)
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))
        whenever(redisTemplate.opsForSet()).thenReturn(redisSetOperations)
        whenever(redisSetOperations.isMember("captains:online", captainId.toString())).thenReturn(true)
        whenever(redisTemplate.hasKey("captains:location:fresh:$captainId")).thenReturn(true)
        whenever(offerRepository.findByCaptainIdAndResponseIsNull(captainId)).thenReturn(listOf(offer))
        whenever(offerRepository.findByCaptainIdAndResponseOrderByRespondedAtDesc(captainId, "ACCEPTED"))
            .thenReturn(listOf(acceptedOffer))
        whenever(jobRepository.findById(activeJobId)).thenReturn(Optional.of(activeJob))

        val error = assertThrows<IllegalStateException> {
            service.respondToOffer(offerId, "ACCEPTED", captainId)
        }

        assertTrue(error.message!!.contains("no longer eligible"))
    }

    @Test
    fun `markPickedUp - accepted captain - records picked-up state`() {
        val restTemplate: RestTemplate = mock()
        val serviceWithRest = DispatchService(
            jobRepository, offerRepository, redisTemplate, kafkaTemplate, entityManager,
            outboxService, idempotencyService, restTemplate = restTemplate
        )
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.ACCEPTED, pickupOtp = "1234")
            .also { it.jobId = jobId }
        val offer = DispatchOffer(jobId = jobId, captainId = captainId, response = "ACCEPTED", offerRank = 1)

        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(offerRepository.findByJobIdAndCaptainId(jobId, captainId)).thenReturn(offer)
        whenever(jobRepository.save(job)).thenReturn(job)
        whenever(restTemplate.exchange(any<String>(), eq(HttpMethod.PUT), any(), eq(Any::class.java)))
            .thenReturn(ResponseEntity.ok<Any>(mapOf("status" to "PICKED_UP")))

        val result = serviceWithRest.markPickedUp(jobId, captainId, "1234")

        assertEquals(JobStatus.PICKED_UP, result.status)
        verify(restTemplate).exchange(
            argThat<String> { contains("status=PICKED_UP") },
            eq(HttpMethod.PUT), any(), eq(Any::class.java)
        )
        verify(outboxService).saveEvent(
            eventId = any(), aggregateType = eq("DISPATCH"), aggregateId = eq(jobId),
            eventType = eq("DispatchJobPickedUp"), eventPayload = any()
        )
    }

    @Test
    fun `wrong pickup OTP cannot move assignment`() {
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.ACCEPTED, pickupOtp = "1234")
            .also { it.jobId = jobId }
        val offer = DispatchOffer(jobId = jobId, captainId = captainId, response = "ACCEPTED", offerRank = 1)
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(offerRepository.findByJobIdAndCaptainId(jobId, captainId)).thenReturn(offer)

        val error = assertThrows<IllegalArgumentException> {
            service.markPickedUp(jobId, captainId, "0000")
        }

        assertTrue(error.message!!.contains("Invalid pickup"))
        assertEquals(JobStatus.ACCEPTED, job.status)
        verify(jobRepository, never()).save(job)
    }

    @Test
    fun `markDelivered - picked-up captain - completes dispatch job`() {
        val restTemplate: RestTemplate = mock()
        val serviceWithRest = DispatchService(
            jobRepository, offerRepository, redisTemplate, kafkaTemplate, entityManager,
            outboxService, idempotencyService, restTemplate = restTemplate
        )
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.PICKED_UP, deliveryOtp = "5678")
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
            eq(HttpMethod.PUT), any(), eq(Any::class.java)
        )
        verify(outboxService).saveEvent(
            eventId = any(), aggregateType = eq("DISPATCH"), aggregateId = eq(jobId),
            eventType = eq("DispatchJobDelivered"), eventPayload = any()
        )
    }

    @Test
    fun `wrong delivery OTP cannot complete picked-up job`() {
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.PICKED_UP, deliveryOtp = "5678")
            .also { it.jobId = jobId }
        val offer = DispatchOffer(jobId = jobId, captainId = captainId, response = "ACCEPTED", offerRank = 1)
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(offerRepository.findByJobIdAndCaptainId(jobId, captainId)).thenReturn(offer)

        val error = assertThrows<IllegalArgumentException> {
            service.markDelivered(jobId, captainId, "1111")
        }

        assertTrue(error.message!!.contains("Invalid handover"))
        assertEquals(JobStatus.PICKED_UP, job.status)
        verify(jobRepository, never()).save(job)
    }

    @Test
    fun `markDelivered - accepted but not picked up - is rejected`() {
        val jobId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.ACCEPTED, deliveryOtp = "5678")
            .also { it.jobId = jobId }
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))

        val error = assertThrows<IllegalStateException> {
            service.markDelivered(jobId, captainId, "5678")
        }
        assertTrue(error.message!!.contains("cannot perform"))
        verify(offerRepository, never()).findByJobIdAndCaptainId(any(), any())
    }

    @Test
    fun `triggerNextOffer - max attempts reached - marks job as FAILED and emits audit event`() {
        val jobId = UUID.randomUUID()
        val job = DispatchJob(
            orderId = UUID.randomUUID(), status = JobStatus.PENDING_ASSIGNMENT,
            attemptCount = 3, maxAttempts = 3
        ).also { it.jobId = jobId }
        whenever(jobRepository.save(any())).thenReturn(job)

        service.triggerNextOffer(job)

        assertEquals(JobStatus.FAILED, job.status)
        assertNotNull(job.resolvedAt)
        verify(jobRepository).save(job)
        verify(outboxService).saveEvent(
            eventId = any(), aggregateType = eq("DISPATCH"), aggregateId = eq(jobId),
            eventType = eq("DispatchJobFailed"), eventPayload = any()
        )
    }

    @Test
    fun `respondToOffer - optimistic locking conflict - throws clean IllegalStateException`() {
        val offerId = UUID.randomUUID()
        val offer = DispatchOffer(
            offerId = offerId, jobId = UUID.randomUUID(), captainId = UUID.randomUUID(),
            offeredAt = Instant.now(), offerRank = 1
        )
        val resolvedOffer = DispatchOffer(
            offerId = offerId, jobId = offer.jobId, captainId = offer.captainId,
            offeredAt = offer.offeredAt, offerRank = offer.offerRank, response = "TIMED_OUT"
        )
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer), Optional.of(resolvedOffer))
        stubEligibleCaptain(offer.captainId, offer.jobId)
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
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.OFFERED).also { it.jobId = jobId }
        val offer = DispatchOffer(
            offerId = UUID.randomUUID(), jobId = jobId, captainId = UUID.randomUUID(),
            offeredAt = Instant.now().minusSeconds(60), offerRank = 1
        )
        whenever(jobRepository.findByStatus(JobStatus.OFFERED)).thenReturn(listOf(job))
        whenever(offerRepository.findByJobIdAndResponseIsNull(jobId)).thenReturn(offer)
        whenever(offerRepository.saveAndFlush(any())).thenThrow(
            ObjectOptimisticLockingFailureException(DispatchOffer::class.java, offer.offerId)
        )

        service.checkOfferTimeouts()

        assertEquals(JobStatus.OFFERED, job.status)
        verify(jobRepository, never()).save(job)
    }

    @Test
    fun `respondToOffer and timeout race - deterministic simulation`() {
        val jobId = UUID.randomUUID()
        val offerId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val job = DispatchJob(orderId = UUID.randomUUID(), status = JobStatus.OFFERED).also { it.jobId = jobId }
        val offer = DispatchOffer(
            offerId = offerId, jobId = jobId, captainId = captainId,
            offeredAt = Instant.now().minusSeconds(60), offerRank = 1
        )
        whenever(offerRepository.findById(offerId)).thenReturn(Optional.of(offer))
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(jobRepository.findByStatus(JobStatus.OFFERED)).thenReturn(listOf(job))
        whenever(offerRepository.findByJobIdAndResponseIsNull(jobId)).thenReturn(offer)
        stubEligibleCaptain(captainId, jobId)
        whenever(restTemplate.exchange(any<String>(), eq(HttpMethod.PUT), any(), eq(Any::class.java)))
            .thenReturn(ResponseEntity.ok(Any()))
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

        val result = service.respondToOffer(offerId, "ACCEPTED", captainId)
        assertEquals("ACCEPTED", result.response)
        assertEquals(JobStatus.ACCEPTED, job.status)
        service.checkOfferTimeouts()
        assertEquals(JobStatus.ACCEPTED, job.status)
    }

    private fun stubEligibleCaptain(captainId: UUID, currentJobId: UUID) {
        whenever(redisTemplate.opsForSet()).thenReturn(redisSetOperations)
        whenever(redisSetOperations.isMember("captains:online", captainId.toString())).thenReturn(true)
        whenever(redisTemplate.hasKey("captains:location:fresh:$captainId")).thenReturn(true)
        whenever(offerRepository.findByCaptainIdAndResponseIsNull(captainId)).thenReturn(emptyList())
        whenever(offerRepository.findByCaptainIdAndResponseOrderByRespondedAtDesc(captainId, "ACCEPTED"))
            .thenReturn(emptyList())
        whenever(offerRepository.findByJobId(currentJobId)).thenReturn(emptyList())
    }
}
