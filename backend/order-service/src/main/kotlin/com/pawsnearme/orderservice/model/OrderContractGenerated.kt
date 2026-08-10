// GENERATED FROM contracts/order-lifecycle.json. DO NOT EDIT BY HAND.
package com.pawsnearme.orderservice.model

enum class OrderStatus {
    PLACED,
    ACCEPTED,
    PREPARING,
    READY_FOR_PICKUP,
    ASSIGNED,
    PICKED_UP,
    DELIVERED,
    COMPLETED,
    REJECTED,
    CANCELLED
}

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    COD_PENDING,
    COD_COLLECTED,
    REFUND_PENDING,
    REFUNDED
}

enum class OrderActor {
    CUSTOMER,
    MERCHANT,
    DISPATCH,
    CAPTAIN,
    SYSTEM
}

data class OrderTransition(
    val actor: OrderActor,
    val fromStatus: OrderStatus,
    val toStatus: OrderStatus
)

object CanonicalOrderContract {
    val transitions: Set<OrderTransition> = setOf(
        OrderTransition(OrderActor.CUSTOMER, OrderStatus.PLACED, OrderStatus.CANCELLED),
        OrderTransition(OrderActor.MERCHANT, OrderStatus.PLACED, OrderStatus.ACCEPTED),
        OrderTransition(OrderActor.MERCHANT, OrderStatus.PLACED, OrderStatus.REJECTED),
        OrderTransition(OrderActor.MERCHANT, OrderStatus.ACCEPTED, OrderStatus.PREPARING),
        OrderTransition(OrderActor.MERCHANT, OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
        OrderTransition(OrderActor.MERCHANT, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP),
        OrderTransition(OrderActor.DISPATCH, OrderStatus.READY_FOR_PICKUP, OrderStatus.ASSIGNED),
        OrderTransition(OrderActor.CAPTAIN, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP),
        OrderTransition(OrderActor.CAPTAIN, OrderStatus.PICKED_UP, OrderStatus.DELIVERED),
        OrderTransition(OrderActor.SYSTEM, OrderStatus.DELIVERED, OrderStatus.COMPLETED)
    )

    fun canTransition(currentStatus: OrderStatus, requestedStatus: OrderStatus, actor: OrderActor): Boolean =
        OrderTransition(actor, currentStatus, requestedStatus) in transitions
}
