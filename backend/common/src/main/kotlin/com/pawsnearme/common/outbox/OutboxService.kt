package com.pawsnearme.common.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OutboxService(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun saveEvent(
        eventId: UUID = UUID.randomUUID(),
        aggregateType: String,
        aggregateId: UUID,
        eventType: String,
        eventPayload: Any
    ) {
        persist(eventId, aggregateType, aggregateId, eventType, eventPayload)
    }

    /**
     * Persist an outbox row in a fresh transaction so it survives when the caller's
     * transaction rolls back (e.g. compensation after a failed order create).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveEventInNewTransaction(
        eventId: UUID = UUID.randomUUID(),
        aggregateType: String,
        aggregateId: UUID,
        eventType: String,
        eventPayload: Any
    ) {
        persist(eventId, aggregateType, aggregateId, eventType, eventPayload)
    }

    private fun persist(
        eventId: UUID,
        aggregateType: String,
        aggregateId: UUID,
        eventType: String,
        eventPayload: Any
    ) {
        val payloadStr = when (eventPayload) {
            is String -> eventPayload
            else -> objectMapper.writeValueAsString(eventPayload)
        }
        val outboxEvent = OutboxEvent(
            eventId = eventId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payloadStr
        )
        outboxRepository.save(outboxEvent)
    }
}
