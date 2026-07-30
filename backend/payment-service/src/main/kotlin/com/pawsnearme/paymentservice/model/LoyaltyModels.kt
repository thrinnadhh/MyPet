package com.pawsnearme.paymentservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class RewardStatus {
    ISSUED, RESERVED, REDEEMED, REVOKED, EXPIRED
}

enum class LedgerEntryType {
    WELCOME_STAR, PURCHASE_STAR, CYCLE_ROLLOVER, STAR_REVERSAL, ADMIN_ADJUSTMENT
}

@Entity
@Table(
    name = "loyalty_programs",
    schema = "payments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider_id"])]
)
class LoyaltyProgram(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "program_id")
    var programId: UUID? = null,

    @Column(name = "provider_id")
    var providerId: UUID? = null, // null for platform-default program

    @Column(name = "target_stars", nullable = false)
    var targetStars: Int = 10,

    @Column(name = "reward_amount", nullable = false)
    var rewardAmount: BigDecimal = BigDecimal("50.00"), // 50.00 or 100.00

    @Column(name = "min_order_value", nullable = false)
    var minOrderValue: BigDecimal = BigDecimal("199.00"),

    @Column(name = "welcome_star_policy", nullable = false)
    var welcomeStarPolicy: Boolean = true,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "is_stackable", nullable = false)
    var isStackable: Boolean = false,

    @Column(name = "expiry_days", nullable = false)
    var expiryDays: Int = 60,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "customer_loyalty_accounts",
    schema = "payments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["customer_id", "provider_id"])]
)
class CustomerLoyaltyAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "account_id")
    var accountId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "star_balance", nullable = false)
    var starBalance: Int = 0,

    @Column(name = "cycle_count", nullable = false)
    var cycleCount: Int = 0,

    @Column(name = "total_stars_earned", nullable = false)
    var totalStarsEarned: Int = 0,

    @Column(name = "total_rewards_issued", nullable = false)
    var totalRewardsIssued: Int = 0,

    @Column(name = "welcome_star_claimed", nullable = false)
    var welcomeStarClaimed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "loyalty_ledger_entries", schema = "payments")
class LoyaltyLedgerEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "entry_id")
    var entryId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "delta_stars", nullable = false)
    var deltaStars: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    var entryType: LedgerEntryType,

    @Column(name = "reference_id")
    var referenceId: UUID? = null,

    @Column(name = "actor_id")
    var actorId: UUID? = null,

    @Column(name = "note")
    var note: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "loyalty_reward_instances", schema = "payments")
class LoyaltyRewardInstance(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "reward_id")
    var rewardId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "reward_amount", nullable = false)
    var rewardAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RewardStatus = RewardStatus.ISSUED,

    @Column(name = "code", nullable = false, unique = true)
    var code: String,

    @Column(name = "order_id")
    var orderId: UUID? = null,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now().plusSeconds(60 * 86400L),

    @Column(name = "used_at")
    var usedAt: Instant? = null
)

@Entity
@Table(
    name = "loyalty_processed_events",
    schema = "payments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_type", "reference_id"])]
)
class LoyaltyProcessedEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "processed_id")
    var processedId: UUID? = null,

    @Column(name = "event_type", nullable = false)
    var eventType: String,

    @Column(name = "reference_id", nullable = false)
    var referenceId: UUID,

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now()
)
