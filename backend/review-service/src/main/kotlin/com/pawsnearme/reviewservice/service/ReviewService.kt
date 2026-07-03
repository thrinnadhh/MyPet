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
        val reviewToSave = if (review.createdAt == null) {
            review.copy(createdAt = Instant.now())
        } else {
            review
        }
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

    fun getProviderAverageRating(providerId: UUID): Double =
        reviewRepo.averageRatingByProvider(providerId) ?: 0.0
}
