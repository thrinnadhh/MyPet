package com.pawsnearme.common.outbox

import org.springframework.stereotype.Component

/**
 * Persistence boundary shared by standalone services and the modular monolith.
 *
 * Standalone services use the local JPA repository and therefore retain their
 * schema-specific JDBC search path. The monolith supplies a primary adapter
 * that qualifies every outbox table explicitly.
 */
interface OutboxPersistence {
    fun save(event: OutboxEvent): OutboxEvent
    fun findUnpublishedEvents(): List<OutboxEvent>
}

@Component
class JpaOutboxPersistence(
    private val outboxRepository: OutboxRepository,
) : OutboxPersistence {
    override fun save(event: OutboxEvent): OutboxEvent = outboxRepository.save(event)

    override fun findUnpublishedEvents(): List<OutboxEvent> =
        outboxRepository.findUnpublishedEvents()
}
