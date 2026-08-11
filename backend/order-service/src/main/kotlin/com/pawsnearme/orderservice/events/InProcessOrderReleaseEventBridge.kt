package com.pawsnearme.orderservice.events

import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.orderservice.service.OrderReleaseReconciliationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "mypet.events",
    name = ["delivery-mode"],
    havingValue = "IN_PROCESS_ONLY"
)
class InProcessOrderReleaseEventBridge(
    private val releaseService: OrderReleaseReconciliationService,
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (event.shadow || event.topic != "orders.events") return
        releaseService.handlePayload(event.payload)
    }
}
