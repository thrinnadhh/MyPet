package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class PaymentService(
    private val transactionRepository: TransactionRepository,
    private val payoutRepository: PayoutRepository,
    private val promotionRepository: PromotionRepository,
    private val orderRefRepository: OrderRefRepository,
    private val appointmentRefRepository: AppointmentRefRepository,
    private val captainEarningRefRepository: CaptainEarningRefRepository,
    private val providerRefRepository: ProviderRefRepository
) {

    @Transactional
    fun calculatePayouts(start: LocalDate, end: LocalDate): List<Payout> {
        val startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        // 1. Get all providers and map providerId -> ownerUserId
        val providers = providerRefRepository.findAll()
        val providerToOwnerMap = providers.associateBy({ it.providerId }, { it.ownerUserId })

        // 2. Fetch completed orders and appointments
        val orders = orderRefRepository.findByStatusAndDeliveredAtBetween("DELIVERED", startInstant, endInstant)
        val appointments = appointmentRefRepository.findByStatusAndCompletedAtBetween("COMPLETED", startInstant, endInstant)

        val merchantPayouts = mutableMapOf<UUID, BigDecimal>()

        // Aggregate orders (100% payout to merchant owner)
        for (order in orders) {
            val ownerId = providerToOwnerMap[order.providerId] ?: continue
            merchantPayouts[ownerId] = merchantPayouts.getOrDefault(ownerId, BigDecimal.ZERO) + order.totalAmount
        }

        // Aggregate appointments (100% payout to merchant owner)
        for (appointment in appointments) {
            val ownerId = providerToOwnerMap[appointment.providerId] ?: continue
            merchantPayouts[ownerId] = merchantPayouts.getOrDefault(ownerId, BigDecimal.ZERO) + appointment.priceAmount
        }

        // 3. Fetch un-payout-ed captain earnings
        val captainEarnings = captainEarningRefRepository.findByPayoutIdIsNullAndEarnedAtBetween(startInstant, endInstant)
        val captainEarningsByCaptain = captainEarnings.groupBy { it.captainId }
        val captainPayouts = captainEarningsByCaptain.mapValues { (_, earnings) ->
            earnings.map { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        }

        val createdPayouts = mutableListOf<Payout>()

        // 4. Save merchant payouts
        for ((ownerUserId, amount) in merchantPayouts) {
            if (amount > BigDecimal.ZERO) {
                val payout = Payout(
                    payeeUserId = ownerUserId,
                    payeeRole = "MERCHANT",
                    amount = amount,
                    status = "PENDING",
                    periodStart = start,
                    periodEnd = end
                )
                createdPayouts.add(payoutRepository.save(payout))
            }
        }

        // 5. Save captain payouts and update earnings links
        for ((captainId, amount) in captainPayouts) {
            if (amount > BigDecimal.ZERO) {
                val payout = Payout(
                    payeeUserId = captainId,
                    payeeRole = "CAPTAIN",
                    amount = amount,
                    status = "PENDING",
                    periodStart = start,
                    periodEnd = end
                )
                val savedPayout = payoutRepository.save(payout)
                createdPayouts.add(savedPayout)

                val earnings = captainEarningsByCaptain[captainId] ?: emptyList()
                for (earning in earnings) {
                    earning.payoutId = savedPayout.payoutId
                    captainEarningRefRepository.save(earning)
                }
            }
        }

        return createdPayouts
    }

    @Transactional
    fun createPromotion(promo: Promotion, creatorRole: String): Promotion {
        // Platform-wide check
        if (promo.providerId == null && creatorRole != "ADMIN") {
            throw IllegalArgumentException("Platform-wide coupons can only be created by ADMIN users")
        }

        if (promotionRepository.existsByCode(promo.code)) {
            throw IllegalArgumentException("Coupon code already exists: ${promo.code}")
        }

        // Discount War Prevention validations
        if (promo.discountType == "FLAT") {
            val minOrder = promo.minOrderValue ?: throw IllegalArgumentException("Minimum order value is required for flat discounts")
            if (promo.discountValue > minOrder * BigDecimal("0.30")) {
                throw IllegalArgumentException("Flat discounts cannot exceed 30% of the minimum order value")
            }
            if (minOrder < promo.discountValue * BigDecimal("1.5")) {
                throw IllegalArgumentException("Minimum order value must be at least 1.5x the discount value")
            }
        } else if (promo.discountType == "PERCENTAGE") {
            if (promo.discountValue > BigDecimal("30.00")) {
                throw IllegalArgumentException("Percentage discounts cannot exceed 30%")
            }
            val maxDiscount = promo.maxDiscountAmount
            val minOrder = promo.minOrderValue
            if (maxDiscount != null && minOrder != null && minOrder < maxDiscount * BigDecimal("1.5")) {
                throw IllegalArgumentException("Minimum order value must be at least 1.5x the maximum discount amount")
            }
        } else {
            throw IllegalArgumentException("Invalid discount type: ${promo.discountType}")
        }

        return promotionRepository.save(promo)
    }

    fun listPromotions(providerId: UUID?): List<Promotion> {
        return if (providerId != null) {
            promotionRepository.findByProviderId(providerId)
        } else {
            promotionRepository.findByProviderIdIsNull()
        }
    }

    fun getPayoutHistory(userId: UUID): List<Payout> {
        return payoutRepository.findByPayeeUserId(userId)
    }

    fun validateCoupon(code: String, orderValue: BigDecimal, providerId: UUID, category: String?): Promotion {
        val promo = promotionRepository.findByCode(code)
            ?: throw IllegalArgumentException("Invalid coupon code")

        if (!promo.isActive) {
            throw IllegalArgumentException("Coupon code is inactive")
        }

        val now = Instant.now()
        if (now.isBefore(promo.validFrom) || now.isAfter(promo.validUntil)) {
            throw IllegalArgumentException("Coupon code has expired")
        }

        if (promo.providerId != null && promo.providerId != providerId) {
            throw IllegalArgumentException("Coupon code is not applicable to this provider")
        }

        if (promo.minOrderValue != null && orderValue < promo.minOrderValue) {
            throw IllegalArgumentException("Minimum order value for this coupon is not met")
        }

        if (promo.applicableCategory != null) {
            if (category == null || !category.equals(promo.applicableCategory, ignoreCase = true)) {
                throw IllegalArgumentException("Coupon is only applicable to category: ${promo.applicableCategory}")
            }
        }

        return promo
    }
}
