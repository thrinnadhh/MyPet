package com.pawsnearme.common.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

open class OutboxPoller(
    private val outboxPersistence: OutboxPersistence,
    private val eventPublisher: OutboxEventPublisher,
) {
    /** Compatibility constructor retained for standalone service configs. */
    constructor(
        outboxRepository: OutboxRepository,
        eventPublisher: OutboxEventPublisher,
    ) : this(
        JpaOutboxPersistence(outboxRepository),
        eventPublisher,
    )

    /**
     * Compatibility constructor retained for every standalone service.
     * It preserves the M0-M5 Kafka-only behavior unless a service opts into
     * RoutedOutboxEventPublisher explicitly.
     */
    constructor(
        outboxRepository: OutboxRepository,
        kafkaTemplate: KafkaTemplate<String, Any>,
        objectMapper: ObjectMapper,
    ) : this(
        JpaOutboxPersistence(outboxRepository),
        KafkaOutboxEventPublisher(kafkaTemplate, objectMapper),
    )

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000, scheduler = "outboxTaskScheduler")
    @SchedulerLock(name = "outbox_pollAndPublish", lockAtMostFor = "PT5S", lockAtLeastFor = "PT1S")
    @Transactional
    open fun pollAndPublish() {
        val pending = outboxPersistence.findUnpublishedEvents()
        if (pending.isEmpty()) return

        log.debug("OutboxPoller: Found ${pending.size} unpublished events")

        for (event in pending) {
            try {
                val receipt = eventPublisher.publish(event)
                event.publishedAt = Instant.now()
                outboxPersistence.save(event)
                log.info(
                    "OutboxPoller: Published event {} to {} kafka={} inProcess={} shadow={}",
                    event.eventId,
                    receipt.topic,
                    receipt.kafkaPublished,
                    receipt.inProcessPublished,
                    receipt.shadow,
                )
            } catch (e: Exception) {
                log.error(
                    "OutboxPoller: Failed to publish event ${event.eventId}: ${e.message}",
                    e,
                )
                // Stop processing to maintain ordering for the current owner.
                break
            }
        }
    }
}
