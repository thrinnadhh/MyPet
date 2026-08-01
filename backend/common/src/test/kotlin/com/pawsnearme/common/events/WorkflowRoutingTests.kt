package com.pawsnearme.common.events

import com.pawsnearme.common.outbox.OutboxDeliveryMode
import com.pawsnearme.common.outbox.OutboxEvent
import com.pawsnearme.common.outbox.OutboxEventPublisher
import com.pawsnearme.common.outbox.OutboxPublishReceipt
import com.pawsnearme.common.outbox.OutboxTopicResolver
import com.pawsnearme.common.outbox.RoutedOutboxEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkflowRoutingTests {

    @Test
    fun `catalog classifies direct event and durable workflows`() {
        val catalog = MyPetWorkflowCatalog.create()

        assertEquals(13, catalog.routes.size)
        assertEquals(3, catalog.count(WorkflowExecutionKind.DIRECT_CALL))
        assertEquals(3, catalog.count(WorkflowExecutionKind.IN_PROCESS_EVENT))
        assertEquals(7, catalog.count(WorkflowExecutionKind.DURABLE_OUTBOX_JOB))
        assertTrue(catalog.hasInProcessReplacement("orders.events"))
        assertTrue(catalog.hasInProcessReplacement("appointments.events"))
        assertFalse(catalog.hasInProcessReplacement("dispatch.events"))
    }

    @Test
    fun `in process mode publishes verified topic without kafka`() {
        val kafka = RecordingOutboxPublisher()
        val moduleEvents = mutableListOf<ModuleDomainEvent>()
        val publisher = RoutedOutboxEventPublisher(
            kafkaPublisher = kafka,
            moduleEventPublisher = ModuleEventPublisher(moduleEvents::add),
            workflowCatalog = MyPetWorkflowCatalog.create(),
            deliveryMode = OutboxDeliveryMode.IN_PROCESS_ONLY
        )

        val receipt = publisher.publish(event("ORDER", "OrderPlaced"))

        assertFalse(receipt.kafkaPublished)
        assertTrue(receipt.inProcessPublished)
        assertFalse(receipt.shadow)
        assertEquals(0, kafka.events.size)
        assertEquals("orders.events", moduleEvents.single().topic)
    }

    @Test
    fun `in process mode rejects topic without verified consumer replacement`() {
        val publisher = RoutedOutboxEventPublisher(
            kafkaPublisher = RecordingOutboxPublisher(),
            moduleEventPublisher = ModuleEventPublisher { },
            workflowCatalog = MyPetWorkflowCatalog.create(),
            deliveryMode = OutboxDeliveryMode.IN_PROCESS_ONLY
        )

        val error = assertThrows(IllegalStateException::class.java) {
            publisher.publish(event("DISPATCH", "DispatchJobOffered"))
        }

        assertTrue(error.message!!.contains("not verified"))
    }

    @Test
    fun `dual shadow keeps kafka authoritative and marks module event shadow`() {
        val kafka = RecordingOutboxPublisher()
        val moduleEvents = mutableListOf<ModuleDomainEvent>()
        val publisher = RoutedOutboxEventPublisher(
            kafkaPublisher = kafka,
            moduleEventPublisher = ModuleEventPublisher(moduleEvents::add),
            workflowCatalog = MyPetWorkflowCatalog.create(),
            deliveryMode = OutboxDeliveryMode.DUAL_SHADOW
        )

        val receipt = publisher.publish(event("REVIEW", "ReviewSubmitted"))

        assertTrue(receipt.kafkaPublished)
        assertTrue(receipt.inProcessPublished)
        assertTrue(receipt.shadow)
        assertEquals(1, kafka.events.size)
        assertTrue(moduleEvents.single().shadow)
    }

    @Test
    fun `topic resolver covers direct durable producers`() {
        assertEquals("chat.events", OutboxTopicResolver.topicFor("CHAT"))
        assertEquals("vaccination.events", OutboxTopicResolver.topicFor("VACCINATION"))
        assertEquals("orders.events", OutboxTopicResolver.topicFor("ORDER"))
    }

    private fun event(aggregateType: String, eventType: String) = OutboxEvent(
        eventId = UUID.randomUUID(),
        aggregateType = aggregateType,
        aggregateId = UUID.randomUUID(),
        eventType = eventType,
        payload = "{\"eventType\":\"$eventType\"}"
    )

    private class RecordingOutboxPublisher : OutboxEventPublisher {
        val events = mutableListOf<OutboxEvent>()

        override fun publish(event: OutboxEvent): OutboxPublishReceipt {
            events += event
            return OutboxPublishReceipt(
                topic = OutboxTopicResolver.topicFor(event.aggregateType),
                kafkaPublished = true,
                inProcessPublished = false,
                shadow = false
            )
        }
    }
}
