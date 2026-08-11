package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class PaymentResultEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val transactionId: UUID,
    val referenceId: UUID,
    val actorId: UUID,
    val amount: BigDecimal,
    val gateway: String,
    val gatewayTransactionId: String?,
    val occurredAt: Instant = Instant.now()
)

data class RegisterLinkedAccountRequest(
    val payeeUserId: UUID,
    @field:Pattern(regexp = "MERCHANT|CAPTAIN")
    val payeeRole: String,
    @field:Pattern(regexp = "[0-9]{6,18}")
    val accountNumber: String,
    @field:Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}")
    val ifsc: String,
    @field:NotBlank
    @field:Size(max = 160)
    val businessName: String,
    @field:Email
    @field:Size(max = 254)
    val email: String
)

/**
 * Non-gateway payment domain service.
 *
 * Customer payment initiation/webhook/refund is Cashfree-owned in
 * [CashfreeGatewayService] and [OrderPaymentLifecycleService]. This service is
 * intentionally limited to transactions reads, coupons, COD policy, promotions
 * and settlement ledger calculations.
 */
@Service
class PaymentService(
    private val transactionRepository: TransactionRepository,
    private val payoutRepository: PayoutRepository,
    private val promotionRepository: PromotionRepository,
    private val orderRefRepository: OrderRefRepository,
    private val appointmentRefRepository: AppointmentRefRepository,
    private val captainEarningRefRepository: CaptainEarningRefRepository,
    private val providerRefRepository: ProviderRefRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
    private val platformCommissionLedgerRepository: PlatformCommissionLedgerRepository,
    private val couponReservationRepository: CouponReservationRepository,
    private val codConfigRepository: CodConfigRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${PAYOUT_SANDBOX_MODE:false}") private val payoutSandboxMode: Boolean = false,
) {
    fun getTransactionById(transactionId: UUID): Transaction =
        transactionRepository.findById(transactionId)
            .orElseThrow { NoSuchElementException("Transaction not found for ID $transactionId") }

    fun getPayoutById(payoutId: UUID): Payout =
        payoutRepository.findById(payoutId)
            .orElseThrow { NoSuchElementException("Payout not found for ID $payoutId") }

    @Transactional
    fun transitionPayoutState(payout: Payout, targetStatus: String, providerTransferId: String? = null): Payout {
        val validTransitions = mapOf(
            "PENDING" to setOf("PROCESSING", "PAID", "FAILED"),
            "PROCESSING" to setOf("PAID", "PROCESSED", "FAILED"),
            "PAID" to setOf("REVERSED"),
            "PROCESSED" to setOf("REVERSED")
        )
        val allowed = validTransitions[payout.status] ?: emptySet()
        if (targetStatus !in allowed) {
            throw IllegalStateException("Invalid payout state transition from ${payout.status} to $targetStatus")
        }
        payout.status = if (targetStatus == "PROCESSED") "PAID" else targetStatus
        if (!providerTransferId.isNullOrBlank()) {
            // Database column retains the legacy name for migration compatibility only.
            payout.razorpayTransferId = providerTransferId
        }
        if (targetStatus == "PAID" || targetStatus == "PROCESSED") payout.paidAt = Instant.now()
        return payoutRepository.save(payout)
    }

    @Transactional
    fun transitionPayoutState(payoutId: UUID, targetStatus: String, providerTransferId: String? = null): Payout =
        transitionPayoutState(getPayoutById(payoutId), targetStatus, providerTransferId)

    @Transactional
    fun calculatePayouts(start: LocalDate, end: LocalDate): List<Payout> {
        if (!payoutSandboxMode) {
            throw IllegalStateException(
                "Cashfree Easy Split payouts are not activated. Refusing to record mock production payouts."
            )
        }
        val startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val createdPayouts = mutableListOf<Payout>()
        val deliveredOrders = orderRefRepository.findByStatusAndDeliveredAtBetween("DELIVERED", startInstant, endInstant)
        val merchantNetAmounts = mutableMapOf<UUID, BigDecimal>()

        if (deliveredOrders.isNotEmpty()) {
            for (order in deliveredOrders) {
                val provider = providerRefRepository.findById(order.providerId).orElse(null)
                val commPct = provider?.commissionPct ?: BigDecimal("15.00")
                val commAmount = order.totalAmount.multiply(commPct)
                    .divide(BigDecimal("100.00"), 2, java.math.RoundingMode.HALF_UP)
                val netAmount = order.totalAmount.subtract(commAmount)
                platformCommissionLedgerRepository.save(
                    PlatformCommissionLedger(
                        providerId = order.providerId,
                        orderId = order.orderId,
                        grossAmount = order.totalAmount,
                        commissionPct = commPct,
                        commissionAmount = commAmount,
                        netMerchantAmount = netAmount
                    )
                )
                val ownerUserId = provider?.ownerUserId ?: order.providerId
                merchantNetAmounts.merge(ownerUserId, netAmount, BigDecimal::add)
            }
        } else {
            orderRefRepository.sumTotalAmountByOwnerAndPeriod("DELIVERED", startInstant, endInstant).forEach { row ->
                merchantNetAmounts.merge(row[1] as UUID, row[2] as BigDecimal, BigDecimal::add)
            }
        }

        appointmentRefRepository.sumPriceAmountByOwnerAndPeriod("COMPLETED", startInstant, endInstant).forEach { row ->
            merchantNetAmounts.merge(row[1] as UUID, row[2] as BigDecimal, BigDecimal::add)
        }

        for ((ownerUserId, grossNet) in merchantNetAmounts) {
            val linkedAccount = linkedAccountRepository.findByPayeeUserId(ownerUserId)
            val pendingClawback = linkedAccount?.pendingClawbackBalance ?: BigDecimal.ZERO
            var netPayout = grossNet.subtract(pendingClawback)
            if (netPayout < BigDecimal.ZERO) {
                if (linkedAccount != null) {
                    linkedAccount.pendingClawbackBalance = pendingClawback.subtract(grossNet)
                    linkedAccountRepository.save(linkedAccount)
                }
                netPayout = BigDecimal.ZERO
            } else if (linkedAccount != null && pendingClawback > BigDecimal.ZERO) {
                linkedAccount.pendingClawbackBalance = BigDecimal.ZERO
                linkedAccountRepository.save(linkedAccount)
            }

            if (netPayout > BigDecimal.ZERO) {
                val transferId = "payout_mock_${UUID.randomUUID().toString().take(12)}"
                createdPayouts += getOrCreatePayout(
                    Payout(
                        payeeUserId = ownerUserId,
                        payeeRole = linkedAccount?.payeeRole ?: "MERCHANT",
                        amount = netPayout,
                        status = "PROCESSING",
                        razorpayTransferId = transferId,
                        periodStart = start,
                        periodEnd = end
                    )
                )
            }
        }

        captainEarningRefRepository.sumAmountByCaptainAndPeriod(startInstant, endInstant).forEach { row ->
            val captainId = row[0] as UUID
            val amount = row[1] as BigDecimal
            if (amount > BigDecimal.ZERO) {
                val savedPayout = getOrCreatePayout(
                    Payout(
                        payeeUserId = captainId,
                        payeeRole = "CAPTAIN",
                        amount = amount,
                        status = "PROCESSING",
                        razorpayTransferId = "payout_mock_${UUID.randomUUID().toString().take(12)}",
                        periodStart = start,
                        periodEnd = end
                    )
                )
                createdPayouts += savedPayout
                captainEarningRefRepository
                    .findByPayoutIdIsNullAndEarnedAtBetweenAndCaptainId(startInstant, endInstant, captainId)
                    .forEach { earning ->
                        earning.payoutId = savedPayout.payoutId
                        captainEarningRefRepository.save(earning)
                    }
            }
        }
        return createdPayouts
    }

    @Transactional
    fun createPromotion(promo: Promotion, creatorRole: String, creatorUserId: UUID?): Promotion {
        promo.code = promo.code.trim().uppercase()
        require(promo.code.matches(Regex("[A-Z0-9_-]{3,64}"))) {
            "Coupon code must be 3-64 characters using letters, numbers, underscore, or hyphen"
        }
        require(promo.usageLimitTotal == null || promo.usageLimitTotal!! > 0) { "Total usage limit must be greater than zero" }
        require(promo.usageLimitPerUser == null || promo.usageLimitPerUser!! > 0) { "Per-user usage limit must be greater than zero" }
        if (promo.providerId == null && creatorRole != "ADMIN") {
            throw IllegalArgumentException("Platform-wide coupons can only be created by ADMIN users")
        }
        if (creatorRole == "MERCHANT") {
            val providerId = promo.providerId ?: throw IllegalArgumentException("Merchant coupons must be scoped to a provider")
            val actorId = creatorUserId ?: throw IllegalArgumentException("Merchant coupon creation requires X-User-Id")
            val provider = providerRefRepository.findById(providerId).orElseThrow {
                IllegalArgumentException("Provider not found: $providerId")
            }
            if (provider.ownerUserId != actorId) throw IllegalArgumentException("Merchant cannot create coupons for a provider they do not own")
        }
        if (promotionRepository.existsByCode(promo.code)) throw IllegalArgumentException("Coupon code already exists: ${promo.code}")

        if (promo.discountType == "FLAT") {
            val minOrder = promo.minOrderValue ?: throw IllegalArgumentException("Minimum order value is required for flat discounts")
            if (promo.discountValue > minOrder * BigDecimal("0.30")) throw IllegalArgumentException("Flat discounts cannot exceed 30% of the minimum order value")
            if (minOrder < promo.discountValue * BigDecimal("1.5")) throw IllegalArgumentException("Minimum order value must be at least 1.5x the discount value")
        } else if (promo.discountType == "PERCENTAGE") {
            if (promo.discountValue > BigDecimal("30.00")) throw IllegalArgumentException("Percentage discounts cannot exceed 30%")
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

    private fun getOrCreatePayout(payout: Payout): Payout =
        payoutRepository.findByPayeeUserIdAndPayeeRoleAndPeriodStartAndPeriodEnd(
            payout.payeeUserId, payout.payeeRole, payout.periodStart, payout.periodEnd
        ) ?: payoutRepository.save(payout)

    fun listPromotions(providerId: UUID?): List<Promotion> =
        if (providerId != null) promotionRepository.findByProviderId(providerId) else promotionRepository.findByProviderIdIsNull()

    fun getPayoutHistory(userId: UUID): List<Payout> = payoutRepository.findByPayeeUserId(userId)

    fun validateCoupon(code: String, orderValue: BigDecimal, providerId: UUID, category: String?): Promotion {
        val promo = promotionRepository.findByCode(code.trim().uppercase())
            ?: throw IllegalArgumentException("Invalid coupon code")
        validateCoupon(promo, orderValue, providerId, category)
        return promo
    }

    private fun validateCoupon(promo: Promotion, orderValue: BigDecimal, providerId: UUID, category: String?) {
        if (!promo.isActive) throw IllegalArgumentException("Coupon code is inactive")
        val now = Instant.now()
        if (now.isBefore(promo.validFrom) || now.isAfter(promo.validUntil)) throw IllegalArgumentException("Coupon code has expired")
        if (promo.providerId != null && promo.providerId != providerId) throw IllegalArgumentException("Coupon code is not applicable to this provider")
        if (promo.minOrderValue != null && orderValue < promo.minOrderValue) throw IllegalArgumentException("Minimum order value for this coupon is not met")
        if (promo.applicableCategory != null && (category == null || !category.equals(promo.applicableCategory, ignoreCase = true))) {
            throw IllegalArgumentException("Coupon is only applicable to category: ${promo.applicableCategory}")
        }
    }

    @Transactional
    fun reserveCoupon(req: CouponReservationRequest): CouponReservationResponse {
        val normalizedCode = req.code.trim().uppercase()
        couponReservationRepository.expireHeldReservations(Instant.now())
        val existing = couponReservationRepository.findByOrderIdAndStatusIn(req.orderId, listOf("HELD", "REDEEMED"))
        if (existing != null) {
            if (existing.code != normalizedCode || existing.userId != req.userId) {
                throw IllegalArgumentException("Order already has a different coupon reservation")
            }
            return existing.toResponse()
        }

        val promo = promotionRepository.findByCodeForUpdate(normalizedCode)
            ?: throw IllegalArgumentException("Invalid coupon code")
        validateCoupon(promo, req.orderValue, req.providerId, req.category)
        if (promo.usageLimitTotal != null) {
            val totalCount = couponReservationRepository.countByPromotionIdAndStatusIn(promo.promotionId!!, listOf("HELD", "REDEEMED"))
            if (totalCount >= promo.usageLimitTotal!!) throw IllegalArgumentException("Total coupon usage limit reached")
        }
        if (promo.usageLimitPerUser != null) {
            val userCount = couponReservationRepository.countByPromotionIdAndUserIdAndStatusIn(
                promo.promotionId!!, req.userId, listOf("HELD", "REDEEMED")
            )
            if (userCount >= promo.usageLimitPerUser!!) throw IllegalArgumentException("User usage limit reached for this coupon")
        }

        val calculatedDiscount = if (promo.discountType == "PERCENTAGE") {
            val raw = req.orderValue.multiply(promo.discountValue).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
            if (promo.maxDiscountAmount != null) raw.min(promo.maxDiscountAmount!!) else raw
        } else {
            promo.discountValue.min(req.orderValue)
        }
        return couponReservationRepository.save(
            CouponReservation(
                promotionId = promo.promotionId!!,
                code = promo.code,
                userId = req.userId,
                orderId = req.orderId,
                discountAmount = calculatedDiscount,
                status = "HELD"
            )
        ).toResponse()
    }

    @Transactional
    fun releaseCouponReservation(code: String, userId: UUID, orderId: UUID) {
        val reservation = couponReservationRepository.findByCodeAndUserIdAndOrderIdAndStatus(
            code.trim().uppercase(), userId, orderId, "HELD"
        ) ?: return
        reservation.status = "RELEASED"
        couponReservationRepository.save(reservation)
    }

    @Transactional
    fun redeemCouponReservation(code: String, userId: UUID, orderId: UUID) {
        val reservation = couponReservationRepository.findByCodeAndUserIdAndOrderIdAndStatus(
            code.trim().uppercase(), userId, orderId, "HELD"
        ) ?: throw IllegalStateException(
            "No active coupon reservation found for code='$code' on order '$orderId'. " +
                "The reservation may have expired — reject this order and request a new quote."
        )
        reservation.status = "REDEEMED"
        couponReservationRepository.save(reservation)
    }

    fun getCodConfig(): Map<String, Any> {
        val globalMax = try {
            codConfigRepository.findById("global_max_amount").map { BigDecimal(it.configValue) }.orElse(BigDecimal("1000.00"))
        } catch (_: Exception) { BigDecimal("1000.00") }
        val cityOverridesStr = try {
            codConfigRepository.findById("city_overrides_json").map { it.configValue }.orElse("{}")
        } catch (_: Exception) { "{}" }
        val disabledCitiesStr = try {
            codConfigRepository.findById("disabled_cities_json").map { it.configValue }.orElse("[]")
        } catch (_: Exception) { "[]" }
        val cityOverrides: Map<String, BigDecimal> = try {
            objectMapper.readValue(cityOverridesStr, object : TypeReference<Map<String, BigDecimal>>() {})
        } catch (_: Exception) { emptyMap() }
        val disabledCities: List<String> = try {
            objectMapper.readValue(disabledCitiesStr, object : TypeReference<List<String>>() {})
        } catch (_: Exception) { emptyList() }
        return mapOf("globalMaxAmount" to globalMax, "cityOverrides" to cityOverrides, "disabledCities" to disabledCities)
    }

    @Transactional
    fun updateCodConfig(req: CodConfigRequest): Map<String, Any> {
        req.globalMaxAmount?.let { require(it > BigDecimal.ZERO && it <= BigDecimal("100000.00")) { "Global COD limit must be between 0 and 100000" } }
        req.cityOverrides?.forEach { (city, amount) ->
            require(city.isNotBlank() && city.length <= 120) { "COD override city is invalid" }
            require(amount > BigDecimal.ZERO && amount <= BigDecimal("100000.00")) { "City COD limits must be between 0 and 100000" }
        }
        req.disabledCities?.forEach { require(it.isNotBlank() && it.length <= 120) { "Disabled COD city is invalid" } }
        if (req.globalMaxAmount != null) {
            val config = codConfigRepository.findById("global_max_amount").orElseGet { CodConfig("global_max_amount", "1000.00") }
            config.configValue = req.globalMaxAmount.toString(); config.updatedAt = Instant.now(); codConfigRepository.save(config)
        }
        if (req.cityOverrides != null) {
            val config = codConfigRepository.findById("city_overrides_json").orElseGet { CodConfig("city_overrides_json", "{}") }
            config.configValue = objectMapper.writeValueAsString(req.cityOverrides); config.updatedAt = Instant.now(); codConfigRepository.save(config)
        }
        if (req.disabledCities != null) {
            val config = codConfigRepository.findById("disabled_cities_json").orElseGet { CodConfig("disabled_cities_json", "[]") }
            config.configValue = objectMapper.writeValueAsString(req.disabledCities); config.updatedAt = Instant.now(); codConfigRepository.save(config)
        }
        return getCodConfig()
    }

    fun checkCodEligibility(req: CodCheckRequest): CodCheckResponse {
        val config = getCodConfig()
        val globalMax = config["globalMaxAmount"] as BigDecimal
        @Suppress("UNCHECKED_CAST") val cityOverrides = config["cityOverrides"] as Map<String, BigDecimal>
        @Suppress("UNCHECKED_CAST") val disabledCities = config["disabledCities"] as List<String>
        if (req.city != null) {
            val normalizedCity = req.city.trim().lowercase()
            if (disabledCities.any { it.trim().lowercase() == normalizedCity }) {
                return CodCheckResponse(false, BigDecimal.ZERO, "COD is disabled in ${req.city}")
            }
            val maxAllowed = cityOverrides.entries.firstOrNull { it.key.trim().lowercase() == normalizedCity }?.value ?: globalMax
            if (req.amount > maxAllowed) {
                return CodCheckResponse(false, maxAllowed, "Order total ₹${req.amount} exceeds COD limit ₹$maxAllowed for ${req.city}")
            }
            return CodCheckResponse(true, maxAllowed)
        }
        if (req.amount > globalMax) return CodCheckResponse(false, globalMax, "Order total ₹${req.amount} exceeds default COD limit ₹$globalMax")
        return CodCheckResponse(true, globalMax)
    }

    fun getGstr8TcsReport(month: String): Gstr8TcsReportResponse {
        val tcsRate = BigDecimal("1.00")
        val payouts = payoutRepository.findAll()
        val entries = payouts.groupBy { it.payeeUserId }.map { (userId, userPayouts) ->
            val gross = userPayouts.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
            val tcs = gross.multiply(tcsRate).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
            Gstr8MerchantEntry(
                providerId = userId,
                gstNumber = "27AAAAA0000A1Z5",
                grossSales = gross,
                netTaxableSales = gross,
                tcsRatePct = tcsRate,
                tcsAmount = tcs
            )
        }
        val totalTaxable = entries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.netTaxableSales) }
        val totalTcs = entries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.tcsAmount) }
        return Gstr8TcsReportResponse(
            month = month,
            totalNetTaxableSales = totalTaxable,
            totalTcsDeducted = totalTcs,
            merchantEntries = entries
        )
    }
}

data class CouponReservationRequest(
    @field:NotBlank @field:Size(max = 64) @field:Pattern(regexp = "[A-Za-z0-9_-]+") val code: String,
    @field:DecimalMin("0.01") @field:DecimalMax("10000000.00") val orderValue: BigDecimal,
    val providerId: UUID,
    val userId: UUID,
    @field:Size(max = 120) val category: String? = null,
    val orderId: UUID
)

data class CouponReservationResponse(
    val reservationId: UUID,
    val code: String,
    val discountAmount: BigDecimal,
    val expiresAt: Instant
)

data class CodConfigRequest(
    @field:DecimalMin("0.01") @field:DecimalMax("100000.00") val globalMaxAmount: BigDecimal? = null,
    val cityOverrides: Map<String, BigDecimal>? = null,
    @field:Size(max = 500) val disabledCities: List<String>? = null
)

data class CodCheckRequest(
    @field:DecimalMin("0.00") @field:DecimalMax("10000000.00") val amount: BigDecimal,
    @field:Size(max = 120) val city: String? = null,
    val providerId: UUID? = null
)

data class CodCheckResponse(val isEligible: Boolean, val maxAllowedAmount: BigDecimal, val reason: String? = null)

private fun CouponReservation.toResponse() = CouponReservationResponse(
    reservationId = reservationId!!,
    code = code,
    discountAmount = discountAmount,
    expiresAt = expiresAt
)

data class Gstr8MerchantEntry(
    val providerId: UUID,
    val gstNumber: String?,
    val grossSales: BigDecimal,
    val netTaxableSales: BigDecimal,
    val tcsRatePct: BigDecimal,
    val tcsAmount: BigDecimal
)

data class Gstr8TcsReportResponse(
    val month: String,
    val totalNetTaxableSales: BigDecimal,
    val totalTcsDeducted: BigDecimal,
    val merchantEntries: List<Gstr8MerchantEntry>
)
