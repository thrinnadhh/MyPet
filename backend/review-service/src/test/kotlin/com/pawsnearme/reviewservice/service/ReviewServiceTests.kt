package com.pawsnearme.reviewservice.service

import com.pawsnearme.reviewservice.model.Review
import com.pawsnearme.reviewservice.repository.ReviewRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.UUID

class ReviewServiceTests {

    private val reviewRepo: ReviewRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val service = ReviewService(reviewRepo, kafkaTemplate)

    private fun buildReview(rating: Int = 4, targetType: String = "PROVIDER") = Review(
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        targetType = targetType,
        targetId = UUID.randomUUID(),
        rating = rating,
        comment = "Good service",
        createdAt = null
    )

    // ── submitReview ──────────────────────────────────────────────────────────

    @Test
    fun `submitReview - duplicate review - throws IllegalStateException`() {
        val review = buildReview()
        whenever(reviewRepo.existsByTargetTypeAndTargetId(review.targetType, review.targetId)).thenReturn(true)

        val ex = assertThrows<IllegalStateException> { service.submitReview(review) }
        assertTrue(ex.message!!.contains("already exists"))
    }

    @Test
    fun `submitReview - success - saves and publishes Kafka event`() {
        val review = buildReview()
        whenever(reviewRepo.existsByTargetTypeAndTargetId(review.targetType, review.targetId)).thenReturn(false)
        whenever(reviewRepo.save(any())).thenReturn(review.copy(createdAt = Instant.now()))

        val result = service.submitReview(review)

        assertNotNull(result.id)
        verify(kafkaTemplate).send(eq("reviews.events"), any(), any())
    }

    @Test
    fun `submitReview - null createdAt - auto-assigns Instant before save`() {
        val review = buildReview().copy(createdAt = null)

        whenever(reviewRepo.existsByTargetTypeAndTargetId(any(), any())).thenReturn(false)
        whenever(reviewRepo.save(any<Review>())).thenAnswer { invocation ->
            val r = invocation.getArgument<Review>(0)
            assertNotNull(r.createdAt, "createdAt must be set before save")
            r.copy(createdAt = Instant.now())
        }

        service.submitReview(review)
        verify(reviewRepo).save(any())
    }

    // ── getProviderAverageRating ──────────────────────────────────────────────

    @Test
    fun `getProviderAverageRating - no reviews - returns 0 dot 0`() {
        val providerId = UUID.randomUUID()
        whenever(reviewRepo.averageRatingByProvider(providerId)).thenReturn(null)
        assertEquals(0.0, service.getProviderAverageRating(providerId))
    }

    @Test
    fun `getProviderAverageRating - with reviews - returns average`() {
        val providerId = UUID.randomUUID()
        whenever(reviewRepo.averageRatingByProvider(providerId)).thenReturn(4.5)
        assertEquals(4.5, service.getProviderAverageRating(providerId))
    }
}
