package com.pawsnearme.reviewservice.model

import jakarta.persistence.*
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "reviews", name = "reviews")
data class Review(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id")
    val id: UUID? = null,

    /** The customer who left the review. */
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    val providerId: UUID,

    /** Polymorphic target: ORDER or APPOINTMENT. Stored as Postgres enum review_target_type. */
    @Column(name = "target_type", nullable = false, columnDefinition = "review_target_type")
    val targetType: String,

    @Column(name = "target_id", nullable = false)
    val targetId: UUID,

    @field:Min(1) @field:Max(5)
    @Column(name = "rating", nullable = false)
    val rating: Int,

    @Column(name = "comment", length = 2000)
    val comment: String? = null,

    /** Optional captain/delivery rating (null for non-dispatch targets). */
    @Column(name = "captain_rating")
    val captainRating: Int? = null,

    @Column(name = "created_at", updatable = false, nullable = false)
    val createdAt: Instant? = Instant.now()
)