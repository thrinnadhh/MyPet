package com.pawsnearme.orderservice.events

import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.orderservice.service.OrderPaymentEventListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "mypet.events",
    name = ["delivery-mode"],
    havingValue = "IN_PROCESS_ONLY"
)
class InProcessOrderPaymentEventBridge(
    private val paymentEventListener: OrderPaymentEventListener,
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (event.shadow || event.topic != "payments.events") return
        paymentEventListener.handlePayload(event.payload)
    }
}
