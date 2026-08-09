package com.pawsnearme.application.runtime

import com.pawsnearme.common.outbox.OutboxEvent
import com.pawsnearme.common.outbox.OutboxPersistence
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

/**
 * Explicit ownership registry for the seven schemas that persist durable
 * outbox events. Identifiers are selected only from this compile-time allowlist
 * and are never derived from request or event payload data.
 */
internal object MonolithOutboxOwnerRegistry {
    val schemas: List<String> = listOf(
        "orders",
        "appointments",
        "providers",
        "catalog",
        "dispatch",
        "reviews",
        "payments",
    )

    private val owners: Map<String, String> = mapOf(
        "ORDER" to "orders",
        "SUPPORT" to "orders",
        "CUSTOMER_CASE" to "orders",
        "RECURRING_ORDER" to "orders",
        "ADMIN_OPERATION" to "orders",
        "APPOINTMENT" to "appointments",
        "PROVIDER" to "providers",
        "PROFILE" to "providers",
        "VACCINATION" to "providers",
        "CATALOG" to "catalog",
        "OFFERING" to "catalog",
        "BILLING" to "catalog",
        "INVENTORY" to "catalog",
        "DISPATCH" to "dispatch",
        "REVIEW" to "reviews",
        "PAYMENT" to "payments",
        "LOYALTY" to "payments",
        "REFUND" to "payments",
        "PAYOUT" to "payments",
    )

    fun schemaFor(aggregateType: String): String = owners[aggregateType.trim().uppercase()]
        ?: throw IllegalArgumentException(
            "No modular-monolith outbox owner is registered for aggregate type '$aggregateType'",
        )
}

/**
 * One application-owned outbox adapter for the modular monolith.
 *
 * The legacy JPA entity deliberately remains unqualified for standalone
 * service compatibility. This adapter replaces that search-path-dependent
 * behavior in the consolidated process by qualifying every insert, select and
 * publication update with its bounded-context schema.
 */
@Component
@Primary
@ConditionalOnProperty(
    prefix = "mypet.runtime",
    name = ["modules-enabled"],
    havingValue = "true",
)
class SchemaQualifiedOutboxPersistence(
    private val jdbcTemplate: JdbcTemplate,
) : OutboxPersistence {

    override fun save(event: OutboxEvent): OutboxEvent {
        val schema = MonolithOutboxOwnerRegistry.schemaFor(event.aggregateType)
        val sql = upsertSql(schema)
        jdbcTemplate.update(sql) { statement ->
            statement.setObject(1, event.eventId)
            statement.setString(2, event.aggregateType)
            statement.setObject(3, event.aggregateId)
            statement.setString(4, event.eventType)
            statement.setObject(5, event.payload, Types.OTHER)
            statement.setTimestamp(6, Timestamp.from(event.createdAt))
            event.publishedAt?.let { statement.setTimestamp(7, Timestamp.from(it)) }
                ?: statement.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE)
        }
        return event
    }

    override fun findUnpublishedEvents(): List<OutboxEvent> =
        MonolithOutboxOwnerRegistry.schemas
            .flatMap { schema ->
                jdbcTemplate.query(selectSql(schema)) { resultSet, _ ->
                    OutboxEvent(
                        eventId = resultSet.getObject("event_id", UUID::class.java),
                        aggregateType = resultSet.getString("aggregate_type"),
                        aggregateId = resultSet.getObject("aggregate_id", UUID::class.java),
                        eventType = resultSet.getString("event_type"),
                        payload = resultSet.getString("payload"),
                        createdAt = resultSet.getTimestamp("created_at").toInstant(),
                        publishedAt = resultSet.getTimestamp("published_at")?.toInstant(),
                    )
                }
            }
            .sortedWith(compareBy<OutboxEvent> { it.createdAt }.thenBy { it.eventId })
            .take(BATCH_SIZE)

    internal fun upsertSql(schema: String): String {
        require(schema in MonolithOutboxOwnerRegistry.schemas) { "Unknown outbox schema '$schema'" }
        return """
            INSERT INTO $schema.outbox_events (
                event_id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                created_at,
                published_at
            )
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            ON CONFLICT (event_id) DO UPDATE SET
                aggregate_type = EXCLUDED.aggregate_type,
                aggregate_id = EXCLUDED.aggregate_id,
                event_type = EXCLUDED.event_type,
                payload = EXCLUDED.payload,
                created_at = EXCLUDED.created_at,
                published_at = EXCLUDED.published_at
        """.trimIndent()
    }

    internal fun selectSql(schema: String): String {
        require(schema in MonolithOutboxOwnerRegistry.schemas) { "Unknown outbox schema '$schema'" }
        return """
            SELECT event_id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at
            FROM $schema.outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at ASC, event_id ASC
            LIMIT $PER_SCHEMA_BATCH_SIZE
            FOR UPDATE SKIP LOCKED
        """.trimIndent()
    }

    override fun markPublished(eventId: UUID, publishedAt: Instant) {
        val updated = MonolithOutboxOwnerRegistry.schemas.sumOf { schema ->
            jdbcTemplate.update(
                "UPDATE $schema.outbox_events SET published_at = ? WHERE event_id = ? AND published_at IS NULL",
                Timestamp.from(publishedAt),
                eventId,
            )
        }
        require(updated <= 1) { "Outbox event $eventId exists in more than one bounded-context schema" }
    }

    companion object {
        private const val BATCH_SIZE = 100
        private const val PER_SCHEMA_BATCH_SIZE = 100
    }
}
