package com.pawsnearme.common.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.common.events.ModuleEventPublisher
import com.pawsnearme.common.events.WorkflowCatalog
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.kafka.core.KafkaTemplate

enum class OutboxDeliveryMode {
    KAFKA_ONLY,
    DUAL_SHADOW,
    IN_PROCESS_ONLY;

    companion object {
        fun parse(value: String?): OutboxDeliveryMode = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        } ?: KAFKA_ONLY
    }
}

data class OutboxPublishReceipt(
    val topic: String,
    val kafkaPublished: Boolean,
    val inProcessPublished: Boolean,
    val shadow: Boolean
)

fun interface OutboxEventPublisher {
    fun publish(event: OutboxEvent): OutboxPublishReceipt
}

object OutboxTopicResolver {
    fun topicFor(aggregateType: String): String = when (aggregateType.uppercase()) {
        "ORDER" -> "orders.events"
        "DISPATCH" -> "dispatch.events"
        "APPOINTMENT" -> "appointments.events"
        "PROVIDER" -> "providers.events"
        "REVIEW" -> "reviews.events"
        "SUPPORT" -> "support.events"
        "PAYMENT" -> "payments.events"
        "CHAT" -> "chat.events"
        "VACCINATION" -> "vaccination.events"
        "CATALOG", "BILLING" -> "catalog.events"
        else -> "${aggregateType.lowercase()}.events"
    }
}

class KafkaOutboxEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) : OutboxEventPublisher {
    override fun publish(event: OutboxEvent): OutboxPublishReceipt {
        val topic = OutboxTopicResolver.topicFor(event.aggregateType)
        val serializerClass = kafkaTemplate.producerFactory.configurationProperties[
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
        ]
        val serializerClassName = when (serializerClass) {
            is Class<*> -> serializerClass.name
            is String -> serializerClass
            else -> ""
        }
        val payloadToSend: Any = if (serializerClassName.contains("StringSerializer")) {
            event.payload
        } else {
            runCatching { objectMapper.readValue(event.payload, Map::class.java) }.getOrElse { event.payload }
        }

        kafkaTemplate.send(topic, event.aggregateId.toString(), payloadToSend).get()
        return OutboxPublishReceipt(
            topic = topic,
            kafkaPublished = true,
            inProcessPublished = false,
            shadow = false
        )
    }
}

class RoutedOutboxEventPublisher(
    private val kafkaPublisher: OutboxEventPublisher,
    private val moduleEventPublisher: ModuleEventPublisher,
    private val workflowCatalog: WorkflowCatalog,
    private val deliveryMode: OutboxDeliveryMode
) : OutboxEventPublisher {
    override fun publish(event: OutboxEvent): OutboxPublishReceipt {
        val topic = OutboxTopicResolver.topicFor(event.aggregateType)
        return when (deliveryMode) {
            OutboxDeliveryMode.KAFKA_ONLY -> kafkaPublisher.publish(event)
            OutboxDeliveryMode.DUAL_SHADOW -> {
                moduleEventPublisher.publish(event.toModuleEvent(topic, shadow = true))
                val kafkaReceipt = kafkaPublisher.publish(event)
                kafkaReceipt.copy(inProcessPublished = true, shadow = true)
            }
            OutboxDeliveryMode.IN_PROCESS_ONLY -> {
                check(workflowCatalog.hasInProcessReplacement(topic)) {
                    "In-process delivery is not verified for topic $topic"
                }
                moduleEventPublisher.publish(event.toModuleEvent(topic, shadow = false))
                OutboxPublishReceipt(
                    topic = topic,
                    kafkaPublished = false,
                    inProcessPublished = true,
                    shadow = false
                )
            }
        }
    }

    private fun OutboxEvent.toModuleEvent(topic: String, shadow: Boolean) = ModuleDomainEvent(
        eventId = eventId,
        topic = topic,
        key = aggregateId.toString(),
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        eventType = eventType,
        payload = payload,
        occurredAt = createdAt,
        shadow = shadow
    )
}
