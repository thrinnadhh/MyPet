package com.pawsnearme.dispatchservice.events

import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.dispatchservice.service.DispatchService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "mypet.events",
    name = ["delivery-mode"],
    havingValue = "IN_PROCESS_ONLY"
)
class InProcessDispatchEventBridge(
    private val dispatchService: DispatchService
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (event.shadow || event.topic != "orders.events") return
        dispatchService.handleOrderStatusChanged(
            ConsumerRecord(event.topic, 0, 0L, event.key, event.payload)
        )
    }
}
