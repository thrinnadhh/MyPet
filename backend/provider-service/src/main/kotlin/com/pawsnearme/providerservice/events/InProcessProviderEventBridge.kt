package com.pawsnearme.providerservice.events

import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.providerservice.service.ReviewEventListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "mypet.events",
    name = ["delivery-mode"],
    havingValue = "IN_PROCESS_ONLY"
)
class InProcessProviderEventBridge(
    private val reviewEventListener: ReviewEventListener
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (!event.shadow && event.topic == "reviews.events") {
            reviewEventListener.onReviewEvent(event.payload)
        }
    }
}
