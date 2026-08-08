package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.JobStatus
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.service.DeliveryContactLookup
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

data class DispatchJobView(
    val jobId: UUID,
    val orderId: UUID,
    val status: JobStatus,
    val attemptCount: Int,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val assignedAt: Instant?,
    val customerPhone: String? = null,
    val customerPhoneVerified: Boolean = false
)

data class DeliveryProofRequest(
    val proofCode: String? = null
)

@RestController
@RequestMapping("/api/v1/dispatch")
class DispatchController(
    private val dispatchService: DispatchService,
    private val offerRepository: DispatchOfferRepository,
    private val jobRepository: DispatchJobRepository,
    private val deliveryContactLookup: DeliveryContactLookup
) {

    @PostMapping("/offers/{offerId}/respond")
    fun respondToOffer(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @PathVariable offerId: UUID,
        @RequestParam response: String
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            return unauthorizedCaptain()
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
            return unauthorizedCaptain()
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

    /**
     * Authenticated captain history and restart-resume source.
     * Delivery contact is exposed only while the accepted job is active.
     * OTP values never leave the dispatch service.
     */
    @GetMapping("/jobs/me")
    fun getMyJobs(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) return unauthorizedCaptain()
        val captainId = UUID.fromString(xUserId)
        val jobs = offerRepository
            .findByCaptainIdAndResponseOrderByRespondedAtDesc(captainId, "ACCEPTED")
            .mapNotNull { offer ->
                jobRepository.findById(offer.jobId).orElse(null)?.let { job ->
                    toView(job, offer.respondedAt)
                }
            }
        return ResponseEntity.ok(jobs)
    }

    /** Administrative queue only; captains use /jobs/me. */
    @GetMapping("/jobs")
    fun listJobs(
        @RequestHeader(value = "X-User-Role", required = false) role: String?,
        @RequestParam(required = false) status: JobStatus?
    ): ResponseEntity<Any> {
        if (role != "ADMIN") return adminOnly()
        val jobs = jobRepository.findAll()
            .filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt }
            .map { toView(it, acceptedOfferFor(it)?.respondedAt) }
        return ResponseEntity.ok(jobs)
    }

    @GetMapping("/jobs/by-order/{orderId}")
    fun getJobByOrderId(
        @RequestHeader(value = "X-User-Role", required = false) role: String?,
        @PathVariable orderId: UUID
    ): ResponseEntity<Any> {
        if (role != "ADMIN") return adminOnly()
        val job = jobRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Job for order $orderId not found")
        return ResponseEntity.ok(toView(job, acceptedOfferFor(job)?.respondedAt))
    }

    @PostMapping("/jobs/{jobId}/pickup")
    fun markPickedUp(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @PathVariable jobId: UUID,
        @RequestBody request: DeliveryProofRequest
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) return unauthorizedCaptain()
        val captainId = UUID.fromString(xUserId)
        val updated = dispatchService.markPickedUp(jobId, captainId, request.proofCode)
        return ResponseEntity.ok(toView(updated, acceptedOfferFor(updated)?.respondedAt))
    }

    @PostMapping("/jobs/{jobId}/deliver")
    fun markDelivered(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @PathVariable jobId: UUID,
        @RequestBody request: DeliveryProofRequest
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) return unauthorizedCaptain()
        val captainId = UUID.fromString(xUserId)
        val updated = dispatchService.markDelivered(jobId, captainId, request.proofCode)
        return ResponseEntity.ok(toView(updated, acceptedOfferFor(updated)?.respondedAt))
    }

    @PostMapping("/admin/check-timeouts")
    fun checkTimeouts(
        @RequestHeader(value = "X-User-Role", required = false) role: String?
    ): ResponseEntity<Any> {
        if (role != "ADMIN") return adminOnly()
        dispatchService.checkOfferTimeouts()
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    private fun acceptedOfferFor(job: DispatchJob) = job.jobId
        ?.let { offerRepository.findByJobId(it).firstOrNull { offer -> offer.response == "ACCEPTED" } }

    private fun toView(job: DispatchJob, assignedAt: Instant?): DispatchJobView {
        val active = job.status == JobStatus.ACCEPTED || job.status == JobStatus.PICKED_UP
        val contact = if (active) deliveryContactLookup.forOrder(job.orderId) else null
        return DispatchJobView(
            jobId = requireNotNull(job.jobId),
            orderId = job.orderId,
            status = job.status,
            attemptCount = job.attemptCount,
            createdAt = job.createdAt,
            resolvedAt = job.resolvedAt,
            assignedAt = assignedAt,
            customerPhone = contact?.phoneNumber,
            customerPhoneVerified = contact?.verified ?: false
        )
    }

    private fun unauthorizedCaptain(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(mapOf("code" to "CAPTAIN_CONTEXT_REQUIRED", "message" to "Missing authenticated captain context."))

    private fun adminOnly(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(mapOf("code" to "ADMIN_REQUIRED", "message" to "Administrator access is required."))
}
