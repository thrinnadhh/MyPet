package com.pawsnearme.common.idempotency

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IdempotencyService(private val processedEventRepository: ProcessedEventRepository) {

    /**
     * Checks if the event has already been processed. If not, records it.
     * Returns true if the event is new, false if it is a duplicate.
     */
    @Transactional
    fun checkAndRecord(eventId: UUID): Boolean {
        if (processedEventRepository.existsById(eventId)) {
            return false
        }
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent(eventId))
            return true
        } catch (e: Exception) {
            // In case of parallel execution causing unique key violation
            return false
        }
    }
}
