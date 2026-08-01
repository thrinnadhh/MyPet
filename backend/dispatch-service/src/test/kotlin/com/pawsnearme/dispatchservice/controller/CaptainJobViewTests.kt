package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.service.DispatchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CaptainJobViewTests {
    private val dispatchService: DispatchService = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val jobRepository: DispatchJobRepository = mock()
    private val controller = DispatchController(dispatchService, offerRepository, jobRepository)

    @Test
    fun `captain job history requires authenticated captain`() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getMyJobs(null).statusCode)
    }

    @Test
    fun `captain job history returns OTP-safe assigned views`() {
        val captainId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val job = DispatchJob(
            jobId = jobId,
            orderId = UUID.randomUUID(),
            status = JobStatus.PICKED_UP,
            pickupOtp = "1234",
            deliveryOtp = "5678"
        )
        val offer = DispatchOffer(
            jobId = jobId,
            captainId = captainId,
            response = "ACCEPTED",
            respondedAt = Instant.now(),
            offerRank = 1
        )
        whenever(offerRepository.findByCaptainIdAndResponseOrderByRespondedAtDesc(captainId, "ACCEPTED"))
            .thenReturn(listOf(offer))
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))

        val response = controller.getMyJobs(captainId.toString())
        val body = response.body as List<*>
        val view = body.single() as DispatchJobView

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(JobStatus.PICKED_UP, view.status)
        assertFalse(view.toString().contains("1234"))
        assertFalse(view.toString().contains("5678"))
    }

    @Test
    fun `global dispatch queue is admin only`() {
        assertEquals(HttpStatus.FORBIDDEN, controller.listJobs("CAPTAIN", null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, controller.getJobByOrderId("CUSTOMER", UUID.randomUUID()).statusCode)
    }
}