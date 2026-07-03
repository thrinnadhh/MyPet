package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.service.DispatchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.UUID

class DispatchControllerTests {

    private val dispatchService: DispatchService = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val jobRepository: DispatchJobRepository = mock()

    private val controller = DispatchController(dispatchService, offerRepository, jobRepository)

    @Test
    fun `respondToOffer - missing authenticated user id - returns 401`() {
        val offerId = UUID.randomUUID()
        val response = controller.respondToOffer(null, offerId, "ACCEPTED")
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `respondToOffer - blank authenticated user id - returns 401`() {
        val offerId = UUID.randomUUID()
        val response = controller.respondToOffer(" ", offerId, "ACCEPTED")
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getActiveOffers - missing authenticated user id - returns 401`() {
        val response = controller.getActiveOffers(null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `markPickedUp - missing authenticated user id - returns 401`() {
        val jobId = UUID.randomUUID()
        val response = controller.markPickedUp(null, jobId, DeliveryProofRequest("1234"))
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `markDelivered - missing authenticated user id - returns 401`() {
        val jobId = UUID.randomUUID()
        val response = controller.markDelivered(null, jobId, DeliveryProofRequest("1234"))
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `respondToOffer - valid authentication - forwards to service`() {
        val offerId = UUID.randomUUID()
        val captainId = UUID.randomUUID()
        val mockOffer = DispatchOffer(jobId = UUID.randomUUID(), captainId = captainId, offerRank = 1)
        whenever(dispatchService.respondToOffer(offerId, "ACCEPTED", captainId)).thenReturn(mockOffer)

        val response = controller.respondToOffer(captainId.toString(), offerId, "ACCEPTED")
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
