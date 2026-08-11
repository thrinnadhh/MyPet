package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.service.DeliveryContactLookup
import com.pawsnearme.dispatchservice.service.DispatchRouteContext
import com.pawsnearme.dispatchservice.service.DispatchRouteContextLookup
import com.pawsnearme.dispatchservice.service.DispatchService
import com.pawsnearme.dispatchservice.service.OrderDeliveryContact
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
    fun `captain job history requires authenticated captain`() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getMyJobs(null).statusCode)
    }

    @Test
    fun `captain job history returns OTP-safe assigned views with active delivery context`() {
        val captainId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val job = DispatchJob(
            jobId = jobId,
            orderId = orderId,
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
        whenever(deliveryContactLookup.forOrder(orderId))
            .thenReturn(OrderDeliveryContact("+919876543210", verified = false))
        whenever(routeContextLookup.forOrder(orderId, captainId)).thenReturn(
            DispatchRouteContext(
                merchantName = "Happy Pets",
                pickupAddress = "Main Road, Tirupati",
                pickupLatitude = 13.63,
                pickupLongitude = 79.42,
                dropAddress = "Customer Home, Tirupati",
                dropLatitude = 13.65,
                dropLongitude = 79.43,
                pickupDistanceKm = 1.4,
                pickupEtaMinutes = 4,
                deliveryDistanceKm = 3.2,
                deliveryEtaMinutes = 8,
            )
        )

        val response = controller.getMyJobs(captainId.toString())
        val body = response.body as List<*>
        val view = body.single() as DispatchJobView

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(JobStatus.PICKED_UP, view.status)
        assertEquals("+919876543210", view.customerPhone)
        assertFalse(view.customerPhoneVerified)
        assertEquals("Happy Pets", view.merchantName)
        assertEquals("Customer Home, Tirupati", view.dropAddress)
        assertEquals(3.2, view.deliveryDistanceKm)
        assertFalse(view.toString().contains("1234"))
        assertFalse(view.toString().contains("5678"))
    }

    @Test
    fun `captain history hides delivery contact and route after job is completed`() {
        val captainId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val job = DispatchJob(jobId = jobId, orderId = orderId, status = JobStatus.COMPLETED)
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
        val view = (response.body as List<*>).single() as DispatchJobView

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(JobStatus.COMPLETED, view.status)
        assertEquals(null, view.customerPhone)
        assertFalse(view.customerPhoneVerified)
        assertEquals(null, view.dropAddress)
    }

    @Test
    fun `global dispatch queue is admin only`() {
        assertEquals(HttpStatus.FORBIDDEN, controller.listJobs("CAPTAIN", null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, controller.getJobByOrderId("CUSTOMER", UUID.randomUUID()).statusCode)
    }
}
