package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.service.DeliveryContactLookup
import com.pawsnearme.dispatchservice.service.DispatchRouteContext
import com.pawsnearme.dispatchservice.service.DispatchRouteContextLookup
import com.pawsnearme.dispatchservice.service.DispatchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

class DispatchControllerTests {

    private val dispatchService: DispatchService = mock()
    private val offerRepository: DispatchOfferRepository = mock()
    private val jobRepository: DispatchJobRepository = mock()
    private val deliveryContactLookup: DeliveryContactLookup = mock()
    private val routeContextLookup: DispatchRouteContextLookup = mock()

    private val controller = DispatchController(
        dispatchService,
        offerRepository,
        jobRepository,
        deliveryContactLookup,
        routeContextLookup,
    )

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

    @Test
    fun `offer exposes pickup route but not customer drop`() {
        val captainId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val offerId = UUID.randomUUID()
        val offer = DispatchOffer(
            offerId = offerId,
            jobId = jobId,
            captainId = captainId,
            offerRank = 1,
        )
        val job = DispatchJob(jobId = jobId, orderId = orderId, status = JobStatus.OFFERED)
        whenever(offerRepository.findByCaptainIdAndResponseIsNull(captainId)).thenReturn(listOf(offer))
        whenever(jobRepository.findById(jobId)).thenReturn(Optional.of(job))
        whenever(routeContextLookup.forOrder(orderId, captainId)).thenReturn(
            DispatchRouteContext(
                merchantName = "Happy Pets",
                pickupAddress = "Main Road, Tirupati",
                pickupLatitude = 13.63,
                pickupLongitude = 79.42,
                dropAddress = "Customer Home",
                dropLatitude = 13.65,
                dropLongitude = 79.43,
                pickupDistanceKm = 1.8,
                pickupEtaMinutes = 5,
                deliveryDistanceKm = 3.4,
                deliveryEtaMinutes = 9,
            )
        )

        val response = controller.getActiveOffers(captainId.toString())
        val view = (response.body as List<*>).single() as DispatchOfferDTO

        assertEquals("Happy Pets", view.merchantName)
        assertEquals("Main Road, Tirupati", view.pickupAddress)
        assertEquals(1.8, view.pickupDistanceKm)
        // Offer DTO intentionally has no customer drop fields; they are exposed only after acceptance.
        assertNull(view.response)
    }
}
