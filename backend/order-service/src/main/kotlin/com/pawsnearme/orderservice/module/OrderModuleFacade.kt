package com.pawsnearme.orderservice.module

import com.pawsnearme.common.module.OrderModuleApi
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.service.OrderService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderModuleFacade(
    private val orderService: OrderService
) : OrderModuleApi {
    override fun updateStatus(orderId: UUID, status: String, actorId: UUID, note: String?) {
        orderService.updateOrderStatus(
            orderId = orderId,
            newStatus = OrderStatus.valueOf(status.trim().uppercase()),
            changedBy = actorId,
            note = note
        )
    }
}
