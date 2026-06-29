package com.pawsnearme.reviewservice.controller

import com.pawsnearme.reviewservice.model.Review
import com.pawsnearme.reviewservice.service.ReviewService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reviews")
class ReviewController(private val reviewService: ReviewService) {

    /**
     * Submit a new review.
     * Body example:
     * {
     *   "customerId": "<uuid>",
     *   "providerId": "<uuid>",
     *   "targetType": "APPOINTMENT",
     *   "targetId":   "<uuid>",
     *   "rating": 5,
     *   "comment": "Great service!"
     * }
     */
    @PostMapping
    fun submitReview(@Valid @RequestBody review: Review): ResponseEntity<Review> {
        return try {
            ResponseEntity.status(HttpStatus.CREATED).body(reviewService.submitReview(review))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /** Get all reviews + average rating for a provider. */
    @GetMapping("/provider/{providerId}")
    fun getByProvider(@PathVariable providerId: UUID): ResponseEntity<Map<String, Any>> {
        val reviews = reviewService.getReviewsByProvider(providerId)
        val avg = reviewService.getProviderAverageRating(providerId)
        return ResponseEntity.ok(
            mapOf(
                "reviews"       to reviews,
                "averageRating" to avg,
                "totalCount"    to reviews.size
            )
        )
    }

    /** Get all reviews submitted by a customer. */
    @GetMapping("/customer/{customerId}")
    fun getByCustomer(@PathVariable customerId: UUID): ResponseEntity<List<Review>> =
        ResponseEntity.ok(reviewService.getReviewsByCustomer(customerId))
}
