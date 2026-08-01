package com.pawsnearme.captainservice.events

import com.pawsnearme.captainservice.service.CaptainService
import com.pawsnearme.common.events.ModuleDomainEvent
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
class InProcessCaptainEventBridge(
    private val captainService: CaptainService
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (event.shadow || event.topic != "orders.events") return
        captainService.handleOrderStatusChanged(
            ConsumerRecord(event.topic, 0, 0L, event.key, event.payload)
        )
    }
}
