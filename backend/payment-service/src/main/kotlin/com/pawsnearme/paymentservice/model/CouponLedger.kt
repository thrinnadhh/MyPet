package com.pawsnearme.paymentservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coupon_reservations", schema = "payments")
class CouponReservation(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "reservation_id")
    var reservationId: UUID? = null,

    @Column(name = "promotion_id", nullable = false)
    var promotionId: UUID,

    @Column(name = "code", nullable = false)
    var code: String,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: BigDecimal,

    @Column(name = "status", nullable = false)
    var status: String = "HELD", // HELD, REDEEMED, RELEASED, EXPIRED

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now().plusSeconds(900)
)

@Entity
@Table(name = "cod_configs", schema = "payments")
class CodConfig(
    @Id
    @Column(name = "config_key")
    var configKey: String,

    @Column(name = "config_value", nullable = false)
    var configValue: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
