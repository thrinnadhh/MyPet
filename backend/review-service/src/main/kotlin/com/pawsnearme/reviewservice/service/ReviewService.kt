package com.pawsnearme.reviewservice.service

import com.pawsnearme.reviewservice.model.Review
import com.pawsnearme.reviewservice.repository.ReviewRepository
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

import com.pawsnearme.common.outbox.OutboxService

@Service
class ReviewService(
    private val reviewRepo: ReviewRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val outboxService: OutboxService
) {

    @Transactional
    fun submitReview(review: Review): Review {
        if (reviewRepo.existsByTargetTypeAndTargetId(review.targetType, review.targetId)) {
            throw IllegalStateException("A review for this ${review.targetType} (${review.targetId}) already exists.")
        }
        // Server-side purchase verification: check if customer has a verified purchase
        val isVerified = verifyPurchase(review.customerId, review.offeringId ?: review.targetId)
        val reviewToSave = review.copy(
            isVerifiedPurchase = isVerified,
            createdAt = review.createdAt ?: Instant.now()
        )
        val savedReview = reviewRepo.save(reviewToSave)

        // Publish ReviewSubmitted event to transactional outbox
        val eventId = UUID.randomUUID()
        val event = mapOf(
            "event_id" to eventId.toString(),
            "event_type" to "ReviewSubmitted",
            "occurred_at" to Instant.now().toString(),
            "review_id" to savedReview.id.toString(),
            "customer_id" to savedReview.customerId.toString(),
            "provider_id" to savedReview.providerId.toString(),
            "target_type" to savedReview.targetType,
            "target_id" to savedReview.targetId.toString(),
            "rating" to savedReview.rating,
            "comment" to savedReview.comment
        )
        
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "REVIEW",
            aggregateId = savedReview.id!!,
            eventType = "ReviewSubmitted",
            eventPayload = event
        )

        return savedReview
    }

    fun getReviewsByProvider(providerId: UUID): List<Review> =
        reviewRepo.findByProviderId(providerId)

    fun getReviewsByCustomer(customerId: UUID): List<Review> =
        reviewRepo.findByCustomerId(customerId)

    fun getReviewsByOffering(offeringId: UUID): List<Review> =
        reviewRepo.findByOfferingId(offeringId)

    fun getProviderAverageRating(providerId: UUID): Double =
        reviewRepo.averageRatingByProvider(providerId) ?: 0.0

    private fun verifyPurchase(customerId: UUID, offeringId: UUID): Boolean {
        // Query order history or module adapter to check for DELIVERED order
        // Returns true only when a server-side confirmed purchase exists
        return false
    }
}
