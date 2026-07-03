package com.pawsnearme.common.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerConfig
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

open class OutboxPoller(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @Transactional
    open fun pollAndPublish() {
        val pending = outboxRepository.findUnpublishedEvents()
        if (pending.isEmpty()) return

        log.debug("OutboxPoller: Found ${pending.size} unpublished events")

        val serializerClass = kafkaTemplate.producerFactory.configurationProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG]
        val serializerClassName = when (serializerClass) {
            is Class<*> -> serializerClass.name
            is String -> serializerClass
            else -> ""
        }
        val isStringSerializer = serializerClassName.contains("StringSerializer")

        for (event in pending) {
            val topic = getTopicForAggregateType(event.aggregateType)
            val key = event.aggregateId.toString()

            val payloadToSend: Any = if (isStringSerializer) {
                event.payload
            } else {
                try {
                    objectMapper.readValue(event.payload, Map::class.java)
                } catch (e: Exception) {
                    event.payload // fallback
                }
            }

            try {
                // Send synchronously to guarantee publish success before marking publishedAt
                kafkaTemplate.send(topic, key, payloadToSend).get()
                event.publishedAt = Instant.now()
                outboxRepository.save(event)
                log.info("OutboxPoller: Published event ${event.eventId} to $topic")
            } catch (e: Exception) {
                log.error("OutboxPoller: Failed to publish event ${event.eventId} to $topic: ${e.message}", e)
                // Stop processing to maintain ordering for partition
                break
            }
        }
    }

    private fun getTopicForAggregateType(aggregateType: String): String {
        return when (aggregateType.uppercase()) {
            "ORDER" -> "orders.events"
            "DISPATCH" -> "dispatch.events"
            "APPOINTMENT" -> "appointments.events"
            "PROVIDER" -> "providers.events"
            "REVIEW" -> "reviews.events"
            "SUPPORT" -> "support.events"
            "PAYMENT" -> "payments.events"
            "CATALOG", "BILLING" -> "catalog.events"
            else -> "${aggregateType.lowercase()}.events"
        }
    }
}
