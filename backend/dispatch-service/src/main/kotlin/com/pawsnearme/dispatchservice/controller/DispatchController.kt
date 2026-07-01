package com.pawsnearme.dispatchservice.controller

import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.service.DispatchService
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

@RestController
@RequestMapping("/api/v1/dispatch")
class DispatchController(
    private val dispatchService: DispatchService,
    private val offerRepository: DispatchOfferRepository,
    private val jobRepository: DispatchJobRepository
) {

    @PostMapping("/offers/{offerId}/respond")
    fun respondToOffer(
        @PathVariable offerId: UUID,
        @RequestParam response: String
    ): ResponseEntity<Any> {
        if (response != "ACCEPTED" && response != "REJECTED") {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid response. Must be ACCEPTED or REJECTED."))
        }
        return try {
            val offer = dispatchService.respondToOffer(offerId, response)
            ResponseEntity.ok(offer)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to respond to offer.")))
        }
    }

    @GetMapping("/offers")
    fun getActiveOffers(
        @RequestHeader(value = "X-User-Id", required = false) xUserId: String?,
        @RequestParam(required = false) captainId: UUID?
    ): ResponseEntity<List<DispatchOfferDTO>> {
        val finalCaptainId = xUserId?.let { UUID.fromString(it) } ?: captainId
            ?: return ResponseEntity.badRequest().build()
        val offers = offerRepository.findByCaptainIdAndResponseIsNull(finalCaptainId)
        val dtos = offers.map { offer ->
            val job = jobRepository.findById(offer.jobId).get()
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
}
