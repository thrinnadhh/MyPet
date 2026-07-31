package com.pawsnearme.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, UUID> {

    /**
     * Atomically claims an event ID.
     *
     * PostgreSQL returns 1 when the row was inserted and 0 when another worker
     * already claimed the same event. Infrastructure errors are deliberately not
     * converted into duplicates; callers must retry rather than acknowledge and
     * permanently skip an event during a database outage.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO processed_events (event_id, processed_at)
            VALUES (:eventId, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(@Param("eventId") eventId: UUID): Int
}
