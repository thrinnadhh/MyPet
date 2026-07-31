package com.pawsnearme.common.idempotency

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
}
