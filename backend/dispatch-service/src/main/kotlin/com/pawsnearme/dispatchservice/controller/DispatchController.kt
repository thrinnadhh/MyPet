package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.service.DispatchService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

data class DispatchOfferDTO(
    val offerId: UUID,
    val jobId: UUID,
    val captainId: UUID,
    val offeredAt: Instant,
    val respondedAt: Instant?,
    val response: String?,
    val offerRank: Int,
    val orderId: UUID
)

data class DeliveryProofRequest(
    val proofCode: String? = null
)

@RestController
@RequestMapping("/api/v1/dispatch")
class DispatchController(
    private val dispatchService: DispatchService,
    private val offerRepository: DispatchOfferRepository,
    private val jobRepository: DispatchJobRepository
) {

    @PostMapping("/offers/{offerId}/respond")
    fun respondToOffer(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @PathVariable offerId: UUID,
        @RequestParam response: String
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated captain context."))
        }
        if (response != "ACCEPTED" && response != "REJECTED") {
            throw IllegalArgumentException("Invalid response. Must be ACCEPTED or REJECTED.")
        }
        val captainId = UUID.fromString(xUserId)
        val offer = dispatchService.respondToOffer(offerId, response, captainId)
        return ResponseEntity.ok(offer)
    }

    @GetMapping("/offers")
    fun getActiveOffers(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated captain context."))
        }
        val finalCaptainId = UUID.fromString(xUserId)
        val offers = offerRepository.findByCaptainIdAndResponseIsNull(finalCaptainId)
        val dtos = offers.map { offer ->
            val job = jobRepository.findById(offer.jobId)
                .orElseThrow { NoSuchElementException("Job ${offer.jobId} not found") }
            DispatchOfferDTO(
                offerId = offer.offerId!!,
                jobId = offer.jobId,
                captainId = offer.captainId,
                offeredAt = offer.offeredAt,
                respondedAt = offer.respondedAt,
                response = offer.response,
                offerRank = offer.offerRank,
                orderId = job.orderId
            )
        }
        return ResponseEntity.ok(dtos)
    }

    @GetMapping("/jobs")
    fun listJobs(
        @RequestParam(required = false) status: JobStatus?
    ): ResponseEntity<List<DispatchJob>> {
        val jobs = jobRepository.findAll()
            .filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt }
        return ResponseEntity.ok(jobs)
    }

    @GetMapping("/jobs/by-order/{orderId}")
    fun getJobByOrderId(
        @PathVariable orderId: UUID
    ): ResponseEntity<Any> {
        val job = jobRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Job for order $orderId not found")
        return ResponseEntity.ok(job)
    }

    @PostMapping("/jobs/{jobId}/pickup")
    fun markPickedUp(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @PathVariable jobId: UUID,
        @RequestBody request: DeliveryProofRequest
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated captain context."))
        }
        val captainId = UUID.fromString(xUserId)
        val updated = dispatchService.markPickedUp(jobId, captainId, request.proofCode)
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/jobs/{jobId}/deliver")
    fun markDelivered(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @PathVariable jobId: UUID,
        @RequestBody request: DeliveryProofRequest
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated captain context."))
        }
        val captainId = UUID.fromString(xUserId)
        val updated = dispatchService.markDelivered(jobId, captainId, request.proofCode)
        return ResponseEntity.ok(updated)
    }
}
