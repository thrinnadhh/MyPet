package com.pawsnearme.discoveryservice.events

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.discoveryservice.service.DiscoveryService
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
class InProcessDiscoveryEventBridge(
    private val discoveryService: DiscoveryService,
    private val objectMapper: ObjectMapper
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (event.shadow || event.topic !in SUPPORTED_TOPICS) return
        val payload = objectMapper.readValue(
            event.payload,
            object : TypeReference<Map<String, Any>>() {}
        )
        val record = ConsumerRecord(event.topic, 0, 0L, event.key, payload)
        when (event.topic) {
            "providers.events" -> discoveryService.handleProviderApproved(record)
            "reviews.events" -> discoveryService.handleReviewSubmitted(record)
        }
    }

    private companion object {
        val SUPPORTED_TOPICS = setOf("providers.events", "reviews.events")
    }
}
