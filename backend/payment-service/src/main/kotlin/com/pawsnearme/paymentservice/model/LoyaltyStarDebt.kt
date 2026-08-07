package com.pawsnearme.paymentservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "loyalty_star_debts", schema = "payments")
class LoyaltyStarDebt(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "debt_id")
    var debtId: UUID? = null,
    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,
    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,
    @Column(name = "debt_stars", nullable = false)
    var debtStars: Int = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
