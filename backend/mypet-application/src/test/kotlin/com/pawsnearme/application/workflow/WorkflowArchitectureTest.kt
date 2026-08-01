package com.pawsnearme.application.workflow

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class WorkflowArchitectureTest {
    private val backendRoot: Path = Path.of(System.getProperty("mypet.backendRoot"))

    @Test
    fun `all current kafka consumer topics have guarded in-process bridges`() {
        val bridges = mapOf(
            "notification-service/src/main/kotlin/com/pawsnearme/notificationservice/events/InProcessNotificationEventBridge.kt" to
                listOf("orders.events", "appointments.events", "chat.events", "vaccination.events"),
            "provider-service/src/main/kotlin/com/pawsnearme/providerservice/events/InProcessProviderEventBridge.kt" to
                listOf("reviews.events"),
            "captain-service/src/main/kotlin/com/pawsnearme/captainservice/events/InProcessCaptainEventBridge.kt" to
                listOf("orders.events"),
            "dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/events/InProcessDispatchEventBridge.kt" to
                listOf("orders.events"),
            "discovery-service/src/main/kotlin/com/pawsnearme/discoveryservice/events/InProcessDiscoveryEventBridge.kt" to
                listOf("providers.events", "reviews.events")
        )

        bridges.forEach { (relativePath, topics) ->
            val source = read(relativePath)
            assertTrue(source.contains("havingValue = \"IN_PROCESS_ONLY\""), "$relativePath must be cutover-guarded")
            assertTrue(source.contains("event.shadow"), "$relativePath must ignore dual-shadow events")
            topics.forEach { topic ->
                assertTrue(source.contains("\"$topic\""), "$relativePath must bridge $topic")
            }
        }
    }

    @Test
    fun `outbox owners retain kafka rollback as default`() {
        listOf(
            "order-service",
            "appointment-service",
            "dispatch-service",
            "payment-service",
            "provider-service",
            "review-service"
        ).forEach { module ->
            val source = read("$module/src/main/kotlin/com/pawsnearme/${module.replace("-service", "service")}/config/OutboxConfig.kt")
            assertTrue(source.contains("mypet.events.delivery-mode:KAFKA_ONLY"), "$module must default to Kafka rollback")
            assertTrue(source.contains("OutboxEventPublisherFactory.create"), "$module must use routed publication")
        }
    }

    @Test
    fun `vaccination publisher uses durable outbox instead of direct kafka send`() {
        val source = read(
            "provider-service/src/main/kotlin/com/pawsnearme/providerservice/service/VaccinationReminderPublisher.kt"
        )
        assertTrue(source.contains("OutboxService"))
        assertTrue(source.contains("aggregateType = \"VACCINATION\""))
        assertFalse(source.contains("KafkaTemplate"))
        assertFalse(source.contains("kafkaTemplate.send"))
    }

    @Test
    fun `outbox poller delegates transport choice and preserves ordering stop`() {
        val source = read("common/src/main/kotlin/com/pawsnearme/common/outbox/OutboxPoller.kt")
        assertTrue(source.contains("OutboxEventPublisher"))
        assertTrue(source.contains("eventPublisher.publish(event)"))
        assertTrue(source.contains("break"))
        assertFalse(source.contains("getTopicForAggregateType"))
    }

    private fun read(relativePath: String): String = Files.readString(backendRoot.resolve(relativePath))
}
