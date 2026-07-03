package com.pawsnearme.common.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface OutboxRepository : JpaRepository<OutboxEvent, UUID> {
    @Query(
        value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findUnpublishedEvents(): List<OutboxEvent>
}
