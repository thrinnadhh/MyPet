package com.pawsnearme.paymentservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "transactions", schema = "payments")
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "transaction_id")
    var transactionId: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "transaction_type", nullable = false)
    var transactionType: String, // ORDER_PAYMENT, APPOINTMENT_PAYMENT, REFUND

    @Column(name = "reference_id", nullable = false)
    var referenceId: UUID,

    @Column(name = "amount", nullable = false)
    var amount: BigDecimal,

    @Column(name = "currency", nullable = false)
    var currency: String = "INR",

    @Column(name = "status", nullable = false)
    var status: String = "PENDING", // PENDING, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED

    @Column(name = "gateway", nullable = false)
    var gateway: String = "RAZORPAY",

    @Column(name = "gateway_transaction_id")
    var gatewayTransactionId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "payouts", schema = "payments")
class Payout(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "payout_id")
    var payoutId: UUID? = null,

    @Column(name = "payee_user_id", nullable = false)
    var payeeUserId: UUID,

    @Column(name = "payee_role", nullable = false)
    var payeeRole: String, // MERCHANT, CAPTAIN

    @Column(name = "amount", nullable = false)
    var amount: BigDecimal,

    @Column(name = "status", nullable = false)
    var status: String = "PENDING", // PENDING, PROCESSING, PAID, FAILED

    @Column(name = "period_start", nullable = false)
    var periodStart: LocalDate,

    @Column(name = "period_end", nullable = false)
    var periodEnd: LocalDate,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "promotions", schema = "payments")
class Promotion(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "promotion_id")
    var promotionId: UUID? = null,

    @Column(name = "provider_id")
    var providerId: UUID? = null,

    @Column(name = "code", unique = true)
    var code: String,

    @Column(name = "discount_type", nullable = false)
    var discountType: String, // PERCENTAGE, FLAT

    @Column(name = "discount_value", nullable = false)
    var discountValue: BigDecimal,

    @Column(name = "max_discount_amount")
    var maxDiscountAmount: BigDecimal? = null,

    @Column(name = "min_order_value")
    var minOrderValue: BigDecimal? = null,

    @Column(name = "valid_from", nullable = false)
    var validFrom: Instant,

    @Column(name = "valid_until", nullable = false)
    var validUntil: Instant,

    @Column(name = "usage_limit_total")
    var usageLimitTotal: Int? = null,

    @Column(name = "usage_limit_per_user")
    var usageLimitPerUser: Int? = null,

    @Column(name = "applicable_category")
    var applicableCategory: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "orders", schema = "orders")
class OrderRef(
    @Id
    @Column(name = "order_id")
    val orderId: UUID,
    
    @Column(name = "provider_id")
    val providerId: UUID,
    
    @Column(name = "captain_id")
    val captainId: UUID?,
    
    @Column(name = "status")
    val status: String,
    
    @Column(name = "total_amount")
    val totalAmount: BigDecimal,
    
    @Column(name = "delivered_at")
    val deliveredAt: Instant?
)

@Entity
@Table(name = "appointments", schema = "appointments")
class AppointmentRef(
    @Id
    @Column(name = "appointment_id")
    val appointmentId: UUID,
    
    @Column(name = "provider_id")
    val providerId: UUID,
    
    @Column(name = "status")
    val status: String,
    
    @Column(name = "price_amount")
    val priceAmount: BigDecimal,
    
    @Column(name = "completed_at")
    val completedAt: Instant?
)

@Entity
@Table(name = "captain_earnings", schema = "captains")
class CaptainEarningRef(
    @Id
    @Column(name = "earning_id")
    val earningId: UUID,
    
    @Column(name = "captain_id")
    val captainId: UUID,
    
    @Column(name = "amount")
    val amount: BigDecimal,
    
    @Column(name = "earned_at")
    val earnedAt: Instant,
    
    @Column(name = "payout_id")
    var payoutId: UUID?
)

@Entity
@Table(name = "providers", schema = "providers")
class ProviderRef(
    @Id
    @Column(name = "provider_id")
    val providerId: UUID,
    
    @Column(name = "owner_user_id")
    val ownerUserId: UUID
)
