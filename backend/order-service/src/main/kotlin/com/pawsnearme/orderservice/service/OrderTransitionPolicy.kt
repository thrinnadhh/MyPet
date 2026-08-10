package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.CanonicalOrderContract
import com.pawsnearme.orderservice.model.OrderActor
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus

class OrderTransitionConflictException(message: String) : RuntimeException(message)

object OrderTransitionPolicy {
    private val merchantActionablePaymentStatuses = setOf(PaymentStatus.COD_PENDING, PaymentStatus.SUCCESS)

    fun validateOrderTransition(
        currentStatus: OrderStatus,
        requestedStatus: OrderStatus,
        actorRole: OrderActor,
        paymentStatus: PaymentStatus
    ) {
        if (!CanonicalOrderContract.canTransition(currentStatus, requestedStatus, actorRole)) {
            throw OrderTransitionConflictException(
                "$actorRole cannot transition order from $currentStatus to $requestedStatus."
            )
        }

        if (
            actorRole == OrderActor.MERCHANT &&
            currentStatus == OrderStatus.PLACED &&
            requestedStatus in setOf(OrderStatus.ACCEPTED, OrderStatus.REJECTED) &&
            paymentStatus !in merchantActionablePaymentStatuses
        ) {
            throw OrderTransitionConflictException(
                "Merchant cannot action a PLACED order while payment status is $paymentStatus."
            )
        }
    }
}
