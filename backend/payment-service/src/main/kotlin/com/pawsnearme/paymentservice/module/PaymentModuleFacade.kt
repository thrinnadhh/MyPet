package com.pawsnearme.paymentservice.module

import com.pawsnearme.common.module.CodEligibilityDecision
import com.pawsnearme.common.module.CouponReservationCommand
import com.pawsnearme.common.module.LoyaltyRewardTerms
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PaymentTransactionSnapshot
import com.pawsnearme.common.module.PrepareOrderPaymentCommand
import com.pawsnearme.common.module.PromotionTerms
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import com.pawsnearme.paymentservice.service.CheckoutLoyaltyService
import com.pawsnearme.paymentservice.service.CodCheckRequest
import com.pawsnearme.paymentservice.service.CouponReservationLifecycleService
import com.pawsnearme.paymentservice.service.CouponReservationRequest
import com.pawsnearme.paymentservice.service.LoyaltyLifecycleService
import com.pawsnearme.paymentservice.service.OrderPaymentLifecycleService
import com.pawsnearme.paymentservice.service.PaymentService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentModuleFacade(
    private val paymentService: PaymentService,
    private val loyaltyLifecycleService: LoyaltyLifecycleService,
    private val orderPaymentLifecycleService: OrderPaymentLifecycleService,
    private val checkoutLoyaltyService: CheckoutLoyaltyService,
    private val cashfreeGatewayService: CashfreeGatewayService,
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

    override fun prepareOrderPayment(command: PrepareOrderPaymentCommand): PaymentTransactionSnapshot =
        orderPaymentLifecycleService.prepare(command)

    override fun expireOrderPayment(orderId: UUID, reason: String): PaymentTransactionSnapshot? =
        orderPaymentLifecycleService.expireOrderPayment(orderId, reason)

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

    override fun loyaltyRewardTerms(rewardId: UUID, customerId: UUID, providerId: UUID): LoyaltyRewardTerms =
        checkoutLoyaltyService.terms(rewardId, customerId, providerId)

    override fun reserveLoyaltyReward(rewardId: UUID, customerId: UUID, providerId: UUID, orderId: UUID) {
        checkoutLoyaltyService.reserve(rewardId, customerId, providerId, orderId)
    }

    override fun releaseLoyaltyReward(rewardId: UUID, customerId: UUID, orderId: UUID) {
        checkoutLoyaltyService.release(rewardId, customerId, orderId)
    }

    override fun redeemLoyaltyReward(rewardId: UUID, customerId: UUID, orderId: UUID) {
        checkoutLoyaltyService.redeem(rewardId, customerId, orderId)
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
        val transaction = cashfreeGatewayService.refundOrder(orderId)
        orderPaymentLifecycleService.publishRefundState(transaction)
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
