package com.pawsnearme.reviewservice.repository

import com.pawsnearme.reviewservice.model.Review
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReviewRepository : JpaRepository<Review, UUID> {

    fun findByProviderId(providerId: UUID): List<Review>

    fun findByCustomerId(customerId: UUID): List<Review>

    /** Guard: one review per target (unique index idx_reviews_target on target_type, target_id). */
    fun existsByTargetTypeAndTargetId(targetType: String, targetId: UUID): Boolean

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.providerId = :providerId")
    fun averageRatingByProvider(providerId: UUID): Double?
}
