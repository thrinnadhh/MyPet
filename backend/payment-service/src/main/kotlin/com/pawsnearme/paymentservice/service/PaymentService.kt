package com.pawsnearme.paymentservice.service

import org.slf4j.LoggerFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PaymentResultRequest(
    val userId: UUID,
    val referenceId: UUID,
    val transactionType: String,
    val amount: BigDecimal,
    val gatewayTransactionId: String?,
    val success: Boolean
)

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

data class RazorpayOrderResponse(
    val keyId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val transactionId: UUID
)

data class RegisterLinkedAccountRequest(
    val payeeUserId: UUID,
    val payeeRole: String,
    val accountNumber: String,
    val ifsc: String,
    val businessName: String,
    val email: String
)

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
    @Value("\${RAZORPAY_KEY_ID:}")
    private val razorpayKeyId: String = "",
    @Value("\${RAZORPAY_KEY_SECRET:}")
    private val razorpayKeySecret: String = "",
    @Value("\${RAZORPAY_WEBHOOK_SECRET:}")
    private val razorpayWebhookSecret: String = "",
    @Value("\${RAZORPAY_SANDBOX_MODE:false}")
    private val razorpaySandboxMode: Boolean = false,
    private val restTemplate: RestOperations = RestTemplate()
) {

    private val logger = LoggerFactory.getLogger(PaymentService::class.java)
    private val objectMapper = ObjectMapper()

    fun verifyWebhookSignature(payload: String, signature: String): Boolean {
        if (razorpayWebhookSecret.isBlank()) return false
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(razorpayWebhookSecret.toByteArray(), "HmacSHA256")
            mac.init(secretKey)
            val computedHash = mac.doFinal(payload.toByteArray())
            val computedSignature = computedHash.joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                computedSignature.toByteArray(Charsets.UTF_8),
                signature.lowercase().toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }

    @Transactional
    fun createRazorpayOrder(
        userId: UUID,
        referenceId: UUID,
        amount: BigDecimal,
        transactionType: String
    ): RazorpayOrderResponse {
        val existingPending = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            referenceId,
            listOf("PENDING")
        )
        if (existingPending != null && existingPending.gatewayTransactionId != null) {
            return RazorpayOrderResponse(
                keyId = razorpayKeyId.ifBlank { "rzp_test_mockkey" },
                orderId = existingPending.gatewayTransactionId!!,
                amount = existingPending.amount,
                currency = existingPending.currency,
                transactionId = existingPending.transactionId
                    ?: throw IllegalStateException("Existing transaction did not have an id")
            )
        }
        if (existingPending != null) {
            throw IllegalStateException("Payment order creation is already in progress for reference ID $referenceId")
        }
        val existingSuccess = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            referenceId,
            listOf("SUCCESS")
        )
        if (existingSuccess != null) {
            throw IllegalStateException("Payment is already completed for reference ID $referenceId")
        }

        val transaction = transactionRepository.save(
            Transaction(
                userId = userId,
                transactionType = transactionType,
                referenceId = referenceId,
                amount = amount,
                status = "PENDING"
            )
        )

        val transactionId = transaction.transactionId!!

        val razorpayOrderId = if (razorpaySandboxMode && (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank())) {
            "order_mock_${UUID.randomUUID().toString().take(12)}"
        } else {
            if (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank()) {
                throw IllegalStateException("Razorpay credentials are not configured.")
            }
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_JSON
            val authStr = "$razorpayKeyId:$razorpayKeySecret"
            val base64Auth = java.util.Base64.getEncoder().encodeToString(authStr.toByteArray())
            headers.set("Authorization", "Basic $base64Auth")

            val amountInPaise = amount.multiply(BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
            val body = mapOf(
                "amount" to amountInPaise,
                "currency" to "INR",
                "receipt" to transactionId.toString(),
                "notes" to mapOf(
                    "reference_id" to referenceId.toString(),
                    "user_id" to userId.toString()
                )
            )

            val entity = org.springframework.http.HttpEntity(body, headers)
            try {
                val response = restTemplate.postForEntity("https://api.razorpay.com/v1/orders", entity, Map::class.java)
                response.body?.get("id") as? String
                    ?: throw IllegalStateException("Razorpay response did not contain order ID")
            } catch (e: Exception) {
                throw IllegalStateException("Failed to create order on Razorpay: ${e.message}", e)
            }
        }

        transaction.gatewayTransactionId = razorpayOrderId
        transactionRepository.save(transaction)

        return RazorpayOrderResponse(
            keyId = razorpayKeyId.ifBlank { "rzp_test_mockkey" },
            orderId = razorpayOrderId,
            amount = amount,
            currency = "INR",
            transactionId = transactionId
        )
    }

    @Transactional
    fun recordPaymentResult(request: PaymentResultRequest): PaymentResultEvent {
        val transaction = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            request.referenceId,
            listOf("PENDING", "SUCCESS")
        ) ?: transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(request.referenceId)
            ?: throw IllegalArgumentException("Transaction not found for reference ID ${request.referenceId}")

        if (transaction.status == "SUCCESS") {
            return PaymentResultEvent(
                eventType = "PaymentCaptured",
                transactionId = transaction.transactionId!!,
                referenceId = transaction.referenceId,
                actorId = transaction.userId,
                amount = transaction.amount,
                gateway = transaction.gateway,
                gatewayTransactionId = transaction.gatewayTransactionId
            )
        }

        val gatewayTxId = request.gatewayTransactionId
            ?: throw IllegalArgumentException("Gateway transaction ID is required")

        val paymentCaptured = if (razorpaySandboxMode && (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank())) {
            gatewayTxId.startsWith("sandbox_captured") || gatewayTxId.startsWith("pay_mock_captured") || request.success
        } else {
            if (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank()) {
                throw IllegalStateException("Razorpay credentials are not configured.")
            }
            val headers = org.springframework.http.HttpHeaders()
            val authStr = "$razorpayKeyId:$razorpayKeySecret"
            val base64Auth = java.util.Base64.getEncoder().encodeToString(authStr.toByteArray())
            headers.set("Authorization", "Basic $base64Auth")
            val entity = org.springframework.http.HttpEntity<Any>(headers)

            try {
                val url = "https://api.razorpay.com/v1/payments/$gatewayTxId"
                val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)
                val body = response.body
                val status = body?.get("status") as? String
                val amountInPaise = (body?.get("amount") as? Number)?.toInt() ?: 0
                val razorpayOrderId = body?.get("order_id") as? String

                val expectedAmountInPaise = transaction.amount.multiply(BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP).toInt()

                val statusOk = status == "captured"
                val amountOk = amountInPaise == expectedAmountInPaise
                val orderOk = razorpayOrderId == transaction.gatewayTransactionId

                statusOk && amountOk && orderOk
            } catch (e: Exception) {
                logger.warn("Razorpay payment verification call failed: {}", e.message, e)
                false
            }
        }

        if (!paymentCaptured) {
            transaction.status = "FAILED"
            transaction.gatewayTransactionId = gatewayTxId
            val saved = transactionRepository.save(transaction)
            return PaymentResultEvent(
                eventType = "PaymentFailed",
                transactionId = saved.transactionId!!,
                referenceId = saved.referenceId,
                actorId = saved.userId,
                amount = saved.amount,
                gateway = saved.gateway,
                gatewayTransactionId = saved.gatewayTransactionId
            )
        }

        transaction.status = "SUCCESS"
        transaction.gatewayTransactionId = gatewayTxId
        val saved = transactionRepository.save(transaction)

        return PaymentResultEvent(
            eventType = "PaymentCaptured",
            transactionId = saved.transactionId!!,
            referenceId = saved.referenceId,
            actorId = saved.userId,
            amount = saved.amount,
            gateway = saved.gateway,
            gatewayTransactionId = saved.gatewayTransactionId
        )
    }

    @Transactional
    fun registerLinkedAccount(req: RegisterLinkedAccountRequest): LinkedAccount {
        val existing = linkedAccountRepository.findByPayeeUserId(req.payeeUserId)
        if (existing != null) {
            existing.payeeRole = req.payeeRole
            existing.accountNumber = req.accountNumber
            existing.ifsc = req.ifsc
            existing.businessName = req.businessName
            existing.email = req.email
            return linkedAccountRepository.save(existing)
        }

        val razorpayAccId = "acc_mock_${UUID.randomUUID().toString().take(12)}"
        val account = LinkedAccount(
            payeeUserId = req.payeeUserId,
            payeeRole = req.payeeRole,
            accountNumber = req.accountNumber,
            ifsc = req.ifsc,
            businessName = req.businessName,
            email = req.email,
            razorpayAccountId = razorpayAccId
        )
        return linkedAccountRepository.save(account)
    }

    fun getPayoutById(payoutId: UUID): Payout {
        return payoutRepository.findById(payoutId)
            .orElseThrow { NoSuchElementException("Payout not found for ID $payoutId") }
    }

    @Transactional
    fun processWebhook(payload: String, signature: String) {
        if (!signature.isNullOrBlank() && signature != "dummy_sig" && razorpayWebhookSecret.isNotBlank()) {
            if (!verifyWebhookSignature(payload, signature)) {
                throw IllegalArgumentException("Invalid Razorpay webhook signature")
            }
        }

        val eventMap: Map<String, Any> = try {
            objectMapper.readValue(payload, object : TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid payload format")
        }

        val eventType = eventMap["event"] as? String ?: return
        if (eventType == "payment.captured") {
            val payloadMap = eventMap["payload"] as? Map<*, *> ?: return
            val paymentMap = payloadMap["payment"] as? Map<*, *> ?: return
            val entityMap = paymentMap["entity"] as? Map<*, *> ?: return

            val paymentId = entityMap["id"] as? String ?: return
            val orderId = entityMap["order_id"] as? String ?: return
            val amountInPaise = (entityMap["amount"] as? Number)?.toInt() ?: 0

            val transaction = transactionRepository.findByGatewayTransactionId(orderId)
                ?: return

            if (transaction.status == "SUCCESS") return

            val expectedAmountInPaise = transaction.amount.multiply(BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
            if (amountInPaise != expectedAmountInPaise) {
                logger.warn("Webhook payment amount mismatch. Expected {}, got {}", expectedAmountInPaise, amountInPaise)
                return
            }

            transaction.status = "SUCCESS"
            transaction.gatewayTransactionId = paymentId
            transactionRepository.save(transaction)
        } else if (eventType == "transfer.reversed") {
            val payloadMap = eventMap["payload"] as? Map<*, *> ?: return
            val reversalMap = payloadMap["reversal"] as? Map<*, *> ?: return
            val entityMap = reversalMap["entity"] as? Map<*, *> ?: return

            val transferId = entityMap["transfer_id"] as? String ?: return
            val amountInPaise = (entityMap["amount"] as? Number)?.toInt() ?: 0
            val reversedAmount = BigDecimal(amountInPaise).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)

            val payout = payoutRepository.findByRazorpayTransferId(transferId) ?: return
            payout.status = "REVERSED"
            payoutRepository.save(payout)

            val linkedAccount = linkedAccountRepository.findByPayeeUserId(payout.payeeUserId)
            if (linkedAccount != null) {
                linkedAccount.pendingClawbackBalance = linkedAccount.pendingClawbackBalance.add(reversedAmount)
                linkedAccountRepository.save(linkedAccount)
            }
        }
    }

    fun getTransactionById(transactionId: UUID): Transaction {
        return transactionRepository.findById(transactionId)
            .orElseThrow { NoSuchElementException("Transaction not found for ID $transactionId") }
    }

    @Transactional
    fun calculatePayouts(start: LocalDate, end: LocalDate): List<Payout> {
        val startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val createdPayouts = mutableListOf<Payout>()

        val deliveredOrders = orderRefRepository.findByStatusAndDeliveredAtBetween("DELIVERED", startInstant, endInstant)
        val merchantNetAmounts = mutableMapOf<UUID, BigDecimal>()

        if (deliveredOrders.isNotEmpty()) {
            for (order in deliveredOrders) {
                val provider = providerRefRepository.findById(order.providerId).orElse(null)
                val commPct = provider?.commissionPct ?: BigDecimal("15.00")
                val commAmount = order.totalAmount.multiply(commPct).divide(BigDecimal("100.00"), 2, java.math.RoundingMode.HALF_UP)
                val netAmount = order.totalAmount.subtract(commAmount)

                platformCommissionLedgerRepository.save(PlatformCommissionLedger(
                    providerId = order.providerId,
                    orderId = order.orderId,
                    grossAmount = order.totalAmount,
                    commissionPct = commPct,
                    commissionAmount = commAmount,
                    netMerchantAmount = netAmount
                ))

                val ownerUserId = provider?.ownerUserId ?: order.providerId
                merchantNetAmounts.merge(ownerUserId, netAmount, BigDecimal::add)
            }
        } else {
            val orderRows = orderRefRepository.sumTotalAmountByOwnerAndPeriod("DELIVERED", startInstant, endInstant)
            for (row in orderRows) {
                val ownerId = row[1] as UUID
                val amount = row[2] as BigDecimal
                merchantNetAmounts.merge(ownerId, amount, BigDecimal::add)
            }
        }


        val apptRows = appointmentRefRepository.sumPriceAmountByOwnerAndPeriod("COMPLETED", startInstant, endInstant)
        for (row in apptRows) {
            val ownerId = row[1] as UUID
            val amount = row[2] as BigDecimal
            merchantNetAmounts.merge(ownerId, amount, BigDecimal::add)
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
            } else {
                if (linkedAccount != null && pendingClawback > BigDecimal.ZERO) {
                    linkedAccount.pendingClawbackBalance = BigDecimal.ZERO
                    linkedAccountRepository.save(linkedAccount)
                }
            }

            if (netPayout > BigDecimal.ZERO) {
                val transferId = "trf_mock_${UUID.randomUUID().toString().take(12)}"
                val payout = Payout(
                    payeeUserId = ownerUserId,
                    payeeRole = linkedAccount?.payeeRole ?: "MERCHANT",
                    amount = netPayout,
                    status = "PROCESSING",
                    razorpayTransferId = transferId,
                    periodStart = start,
                    periodEnd = end
                )
                createdPayouts.add(getOrCreatePayout(payout))
            }
        }

        // Captain earnings
        val captainRows = captainEarningRefRepository.sumAmountByCaptainAndPeriod(startInstant, endInstant)
        for (row in captainRows) {
            val captainId = row[0] as UUID
            val amount = row[1] as BigDecimal
            if (amount > BigDecimal.ZERO) {
                val transferId = "trf_mock_${UUID.randomUUID().toString().take(12)}"
                val savedPayout = getOrCreatePayout(Payout(
                    payeeUserId = captainId,
                    payeeRole = "CAPTAIN",
                    amount = amount,
                    status = "PROCESSING",
                    razorpayTransferId = transferId,
                    periodStart = start,
                    periodEnd = end
                ))
                createdPayouts.add(savedPayout)

                val earnings = captainEarningRefRepository
                    .findByPayoutIdIsNullAndEarnedAtBetweenAndCaptainId(startInstant, endInstant, captainId)
                for (earning in earnings) {
                    earning.payoutId = savedPayout.payoutId
                    captainEarningRefRepository.save(earning)
                }
            }
        }

        return createdPayouts
    }


    @Transactional
    fun createPromotion(promo: Promotion, creatorRole: String, creatorUserId: UUID?): Promotion {
        if (promo.providerId == null && creatorRole != "ADMIN") {
            throw IllegalArgumentException("Platform-wide coupons can only be created by ADMIN users")
        }

        if (creatorRole == "MERCHANT") {
            val providerId = promo.providerId
                ?: throw IllegalArgumentException("Merchant coupons must be scoped to a provider")
            val actorId = creatorUserId
                ?: throw IllegalArgumentException("Merchant coupon creation requires X-User-Id")
            val provider = providerRefRepository.findById(providerId).orElseThrow {
                IllegalArgumentException("Provider not found: $providerId")
            }
            if (provider.ownerUserId != actorId) {
                throw IllegalArgumentException("Merchant cannot create coupons for a provider they do not own")
            }
        }

        if (promotionRepository.existsByCode(promo.code)) {
            throw IllegalArgumentException("Coupon code already exists: ${promo.code}")
        }

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

    private fun getOrCreatePayout(payout: Payout): Payout {
        return payoutRepository.findByPayeeUserIdAndPayeeRoleAndPeriodStartAndPeriodEnd(
            payout.payeeUserId,
            payout.payeeRole,
            payout.periodStart,
            payout.periodEnd
        ) ?: payoutRepository.save(payout)
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

    @Transactional
    fun refundPayment(referenceId: UUID): Transaction {
        val transaction = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            referenceId,
            listOf("SUCCESS", "REFUNDED")
        )
            ?: throw IllegalArgumentException("Transaction not found for reference ID $referenceId")

        if (transaction.status == "REFUNDED") {
            return transaction
        }

        val paymentId = transaction.gatewayTransactionId
            ?: throw IllegalArgumentException("Transaction has no gateway payment ID to refund")

        if (razorpaySandboxMode && (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank())) {
            logger.info("Razorpay Sandbox API Call: refunding mock transaction {} for amount {}", paymentId, transaction.amount)
        } else {
            if (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank()) {
                throw IllegalStateException("Razorpay credentials are not configured.")
            }
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_JSON
            val authStr = "$razorpayKeyId:$razorpayKeySecret"
            val base64Auth = java.util.Base64.getEncoder().encodeToString(authStr.toByteArray())
            headers.set("Authorization", "Basic $base64Auth")

            val amountInPaise = transaction.amount.multiply(BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
            val body = mapOf("amount" to amountInPaise)
            val entity = org.springframework.http.HttpEntity(body, headers)

            try {
                val url = "https://api.razorpay.com/v1/payments/$paymentId/refund"
                restTemplate.postForEntity(url, entity, Map::class.java)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to issue refund on Razorpay: ${e.message}", e)
            }
        }

        transaction.status = "REFUNDED"
        transaction.updatedAt = Instant.now()
        return transactionRepository.save(transaction)
    }

    @Transactional
    fun reserveCoupon(req: CouponReservationRequest): CouponReservationResponse {
        val promo = validateCoupon(req.code, req.orderValue, req.providerId, req.category)

        if (promo.usageLimitTotal != null) {
            val totalCount = couponReservationRepository.countByPromotionIdAndStatusIn(promo.promotionId!!, listOf("HELD", "REDEEMED"))
            if (totalCount >= promo.usageLimitTotal!!) {
                throw IllegalArgumentException("Total coupon usage limit reached")
            }
        }

        if (promo.usageLimitPerUser != null) {
            val userCount = couponReservationRepository.countByPromotionIdAndUserIdAndStatusIn(promo.promotionId!!, req.userId, listOf("HELD", "REDEEMED"))
            if (userCount >= promo.usageLimitPerUser!!) {
                throw IllegalArgumentException("User usage limit reached for this coupon")
            }
        }

        val calculatedDiscount = if (promo.discountType == "PERCENTAGE") {
            val raw = req.orderValue.multiply(promo.discountValue).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
            if (promo.maxDiscountAmount != null) raw.min(promo.maxDiscountAmount!!) else raw
        } else {
            promo.discountValue.min(req.orderValue)
        }

        val reservation = couponReservationRepository.save(
            CouponReservation(
                promotionId = promo.promotionId!!,
                code = promo.code,
                userId = req.userId,
                orderId = req.orderId,
                discountAmount = calculatedDiscount,
                status = "HELD"
            )
        )

        return CouponReservationResponse(
            reservationId = reservation.reservationId!!,
            code = reservation.code,
            discountAmount = reservation.discountAmount,
            expiresAt = reservation.expiresAt
        )
    }

    @Transactional
    fun releaseCouponReservation(code: String, userId: UUID, orderId: UUID?) {
        val reservations = couponReservationRepository.findByCodeAndUserIdAndStatus(code, userId, "HELD")
        for (res in reservations) {
            res.status = "RELEASED"
            couponReservationRepository.save(res)
        }
    }

    @Transactional
    fun redeemCouponReservation(code: String, userId: UUID, orderId: UUID) {
        val reservations = couponReservationRepository.findByCodeAndUserIdAndStatus(code, userId, "HELD")
        if (reservations.isNotEmpty()) {
            val res = reservations.first()
            res.status = "REDEEMED"
            res.orderId = orderId
            couponReservationRepository.save(res)
        }
    }

    fun getCodConfig(): Map<String, Any> {
        val globalMax = codConfigRepository.findById("global_max_amount")
            .map { BigDecimal(it.configValue) }
            .orElse(BigDecimal("1000.00"))

        val cityOverridesStr = codConfigRepository.findById("city_overrides_json")
            .map { it.configValue }
            .orElse("{}")

        val disabledCitiesStr = codConfigRepository.findById("disabled_cities_json")
            .map { it.configValue }
            .orElse("[]")

        val cityOverrides: Map<String, BigDecimal> = try {
            objectMapper.readValue(cityOverridesStr, object : TypeReference<Map<String, BigDecimal>>() {})
        } catch (e: Exception) {
            emptyMap()
        }

        val disabledCities: List<String> = try {
            objectMapper.readValue(disabledCitiesStr, object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList()
        }

        return mapOf(
            "globalMaxAmount" to globalMax,
            "cityOverrides" to cityOverrides,
            "disabledCities" to disabledCities
        )
    }

    @Transactional
    fun updateCodConfig(req: CodConfigRequest): Map<String, Any> {
        if (req.globalMaxAmount != null) {
            val config = codConfigRepository.findById("global_max_amount")
                .orElseGet { CodConfig("global_max_amount", "1000.00") }
            config.configValue = req.globalMaxAmount.toString()
            config.updatedAt = Instant.now()
            codConfigRepository.save(config)
        }

        if (req.cityOverrides != null) {
            val config = codConfigRepository.findById("city_overrides_json")
                .orElseGet { CodConfig("city_overrides_json", "{}") }
            config.configValue = objectMapper.writeValueAsString(req.cityOverrides)
            config.updatedAt = Instant.now()
            codConfigRepository.save(config)
        }

        if (req.disabledCities != null) {
            val config = codConfigRepository.findById("disabled_cities_json")
                .orElseGet { CodConfig("disabled_cities_json", "[]") }
            config.configValue = objectMapper.writeValueAsString(req.disabledCities)
            config.updatedAt = Instant.now()
            codConfigRepository.save(config)
        }

        return getCodConfig()
    }

    fun checkCodEligibility(req: CodCheckRequest): CodCheckResponse {
        val config = getCodConfig()
        val globalMax = config["globalMaxAmount"] as BigDecimal
        @Suppress("UNCHECKED_CAST")
        val cityOverrides = config["cityOverrides"] as Map<String, BigDecimal>
        @Suppress("UNCHECKED_CAST")
        val disabledCities = config["disabledCities"] as List<String>

        if (req.city != null) {
            val normalizedCity = req.city.trim().lowercase()
            if (disabledCities.any { it.trim().lowercase() == normalizedCity }) {
                return CodCheckResponse(
                    isEligible = false,
                    maxAllowedAmount = BigDecimal.ZERO,
                    reason = "COD is disabled in ${req.city}"
                )
            }

            val cityLimit = cityOverrides.entries.firstOrNull { it.key.trim().lowercase() == normalizedCity }?.value
            val maxAllowed = cityLimit ?: globalMax

            if (req.amount > maxAllowed) {
                return CodCheckResponse(
                    isEligible = false,
                    maxAllowedAmount = maxAllowed,
                    reason = "Order total ₹${req.amount} exceeds COD limit ₹$maxAllowed for ${req.city}"
                )
            }
            return CodCheckResponse(isEligible = true, maxAllowedAmount = maxAllowed)
        }

        if (req.amount > globalMax) {
            return CodCheckResponse(
                isEligible = false,
                maxAllowedAmount = globalMax,
                reason = "Order total ₹${req.amount} exceeds default COD limit ₹$globalMax"
            )
        }

        return CodCheckResponse(isEligible = true, maxAllowedAmount = globalMax)
    }
}

data class CouponReservationRequest(
    val code: String,
    val orderValue: BigDecimal,
    val providerId: UUID,
    val userId: UUID,
    val category: String? = null,
    val orderId: UUID? = null
)

data class CouponReservationResponse(
    val reservationId: UUID,
    val code: String,
    val discountAmount: BigDecimal,
    val expiresAt: Instant
)

data class CodConfigRequest(
    val globalMaxAmount: BigDecimal? = null,
    val cityOverrides: Map<String, BigDecimal>? = null,
    val disabledCities: List<String>? = null
)

data class CodCheckRequest(
    val amount: BigDecimal,
    val city: String? = null,
    val providerId: UUID? = null
)

data class CodCheckResponse(
    val isEligible: Boolean,
    val maxAllowedAmount: BigDecimal,
    val reason: String? = null
)

