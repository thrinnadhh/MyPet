package com.pawsnearme.dispatchservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class JobStatus {
    PENDING_ASSIGNMENT, OFFERED, ACCEPTED, PICKED_UP, REJECTED, TIMED_OUT, COMPLETED, FAILED
}

@Entity
@Table(name = "dispatch_jobs", schema = "dispatch")
class DispatchJob(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "job_id")
    var jobId: UUID? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: JobStatus = JobStatus.PENDING_ASSIGNMENT,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 3,

    @Column(name = "pickup_otp")
    var pickupOtp: String? = null,

    @Column(name = "delivery_otp")
    var deliveryOtp: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
)

@Entity
@Table(name = "dispatch_offers", schema = "dispatch")
class DispatchOffer(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "offer_id")
    var offerId: UUID? = null,

    @Column(name = "job_id", nullable = false)
    var jobId: UUID,

    @Column(name = "captain_id", nullable = false)
    var captainId: UUID,

    @Column(name = "offered_at", nullable = false)
    var offeredAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,

    @Column(name = "response")
    var response: String? = null, // 'ACCEPTED', 'REJECTED', 'TIMED_OUT'

    @Column(name = "offer_rank", nullable = false)
    var offerRank: Int,

    @Version
    @Column(name = "version")
    var version: Long = 0
)