package com.pawsnearme.common.idempotency

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class IdempotencyService(private val processedEventRepository: ProcessedEventRepository) {

    /**
     * Atomically records a new event.
     *
     * Returns true only when this worker claimed the event. Duplicate events return
     * false through PostgreSQL ON CONFLICT semantics. Database and infrastructure
     * failures propagate so the message/webhook is retried instead of being
     * incorrectly acknowledged as a duplicate.
     */
    @Transactional
    fun checkAndRecord(eventId: UUID): Boolean =
        processedEventRepository.insertIfAbsent(eventId) == 1

    /**
     * Atomically records a transport event for one logical consumer.
     *
     * In the modular monolith, bounded contexts share one datasource and the
     * unqualified processed_events repository can therefore resolve to the same
     * physical table for multiple Kafka consumer groups. Deriving a deterministic
     * UUID from the consumer scope and transport event ID preserves per-consumer
     * idempotency without requiring a destructive schema migration.
     */
    @Transactional
    fun checkAndRecord(consumerScope: String, eventId: UUID): Boolean {
        require(consumerScope.isNotBlank()) { "Consumer scope must not be blank" }
        val scopedEventId = UUID.nameUUIDFromBytes(
            "$consumerScope:$eventId".toByteArray(StandardCharsets.UTF_8)
        )
        return processedEventRepository.insertIfAbsent(scopedEventId) == 1
    }
}
