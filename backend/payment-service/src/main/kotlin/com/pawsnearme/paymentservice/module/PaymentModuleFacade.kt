package com.pawsnearme.paymentservice.module

import com.pawsnearme.common.module.CodEligibilityDecision
import com.pawsnearme.common.module.CouponReservationCommand
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PaymentTransactionSnapshot
import com.pawsnearme.common.module.PromotionTerms
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import com.pawsnearme.paymentservice.service.CodCheckRequest
import com.pawsnearme.paymentservice.service.CouponReservationLifecycleService
import com.pawsnearme.paymentservice.service.CouponReservationRequest
import com.pawsnearme.paymentservice.service.LoyaltyLifecycleService
import com.pawsnearme.paymentservice.service.PaymentService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentModuleFacade(
    private val paymentService: PaymentService,
    private val cashfreeGatewayService: CashfreeGatewayService,
    private val loyaltyLifecycleService: LoyaltyLifecycleService,
    private val couponReservationLifecycleService: CouponReservationLifecycleService,
) : PaymentModuleApi {

    override fun transaction(transactionId: UUID): PaymentTransactionSnapshot? =
        runCatching { paymentService.getTransactionById(transactionId) }
            .getOrNull()
            ?.let { transaction ->
                PaymentTransactionSnapshot(
                    transactionId = requireNotNull(transaction.transactionId) {
                        "Payment transaction is missing its identifier"
                    },
                    userId = transaction.userId,
                    referenceId = transaction.referenceId,
                    transactionType = transaction.transactionType,
                    amount = transaction.amount,
                    status = transaction.status
                )
            }

    override fun promotionTerms(
        code: String,
        orderValue: BigDecimal,
        providerId: UUID,
        category: String?
    ): PromotionTerms = paymentService.validateCoupon(code, orderValue, providerId, category).let { promo ->
        PromotionTerms(
            discountType = promo.discountType,
            discountValue = promo.discountValue,
            maxDiscountAmount = promo.maxDiscountAmount
        )
    }

    override fun reserveCoupon(command: CouponReservationCommand): BigDecimal =
        paymentService.reserveCoupon(
            CouponReservationRequest(
                code = command.code,
                orderValue = command.orderValue,
                providerId = command.providerId,
                userId = command.userId,
                category = command.category,
                orderId = command.orderId
            )
        ).discountAmount

    override fun releaseCoupon(code: String, userId: UUID, orderId: UUID) {
        couponReservationLifecycleService.release(code, userId, orderId)
    }

    override fun redeemCoupon(code: String, userId: UUID, orderId: UUID) {
        paymentService.redeemCouponReservation(code, userId, orderId)
    }

    override fun codEligibility(
        amount: BigDecimal,
        city: String?,
        providerId: UUID?
    ): CodEligibilityDecision = paymentService.checkCodEligibility(
        CodCheckRequest(amount = amount, city = city, providerId = providerId)
    ).let { decision ->
        CodEligibilityDecision(
            eligible = decision.isEligible,
            maxAllowedAmount = decision.maxAllowedAmount,
            reason = decision.reason
        )
    }

    override fun refundOrder(orderId: UUID) {
        // Customer and appointment checkout are Cashfree-backed. Admin dispute
        // resolution must use the same authoritative gateway path instead of the
        // retained Razorpay compatibility implementation in PaymentService.
        cashfreeGatewayService.refundOrder(orderId)
    }

    override fun recordOrderDelivered(
        orderId: UUID,
        customerId: UUID,
        providerId: UUID,
        netAmount: BigDecimal
    ) {
        loyaltyLifecycleService.recordDelivered(orderId, customerId, providerId, netAmount)
    }

    override fun recordOrderRefunded(orderId: UUID, customerId: UUID, providerId: UUID) {
        loyaltyLifecycleService.recordRefunded(orderId, customerId, providerId)
    }
}
