package com.pawsnearme.orderservice.module

import com.pawsnearme.common.module.OrderModuleApi
import com.pawsnearme.orderservice.model.OrderActor
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.service.OrderService
import com.pawsnearme.orderservice.service.OrderTransitionConflictException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderModuleFacade(
    private val orderService: OrderService
) : OrderModuleApi {
    override fun updateStatus(orderId: UUID, status: String, actorId: UUID, note: String?) {
        val requestedStatus = OrderStatus.valueOf(status.trim().uppercase())
        val actorRole = when (requestedStatus) {
            OrderStatus.ASSIGNED -> OrderActor.DISPATCH
            OrderStatus.PICKED_UP, OrderStatus.DELIVERED -> OrderActor.CAPTAIN
            else -> throw OrderTransitionConflictException(
                "Order module dispatch boundary cannot request status $requestedStatus."
            )
        }
        orderService.updateOrderStatus(
            orderId = orderId,
            newStatus = requestedStatus,
            changedBy = actorId,
            actorRole = actorRole,
            note = note
        )
    }
}
