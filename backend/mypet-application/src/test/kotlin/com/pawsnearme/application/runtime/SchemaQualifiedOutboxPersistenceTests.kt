package com.pawsnearme.application.runtime

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class SchemaQualifiedOutboxPersistenceTests {
    private val persistence = SchemaQualifiedOutboxPersistence(JdbcTemplate())

    @Test
    fun `aggregate owners resolve to their bounded-context schemas`() {
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("ORDER")).isEqualTo("orders")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("support")).isEqualTo("orders")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("RECURRING_ORDER")).isEqualTo("orders")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("ADMIN_OPERATION")).isEqualTo("orders")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("APPOINTMENT")).isEqualTo("appointments")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("VACCINATION")).isEqualTo("providers")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("CATALOG")).isEqualTo("catalog")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("OFFERING")).isEqualTo("catalog")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("BILLING")).isEqualTo("catalog")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("DISPATCH")).isEqualTo("dispatch")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("REVIEW")).isEqualTo("reviews")
        assertThat(MonolithOutboxOwnerRegistry.schemaFor("LOYALTY")).isEqualTo("payments")
    }

    @Test
    fun `unknown aggregate owners fail closed`() {
        assertThatThrownBy { MonolithOutboxOwnerRegistry.schemaFor("UNREGISTERED") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No modular-monolith outbox owner")
    }

    @Test
    fun `schema-qualified statements use only registered identifiers`() {
        MonolithOutboxOwnerRegistry.schemas.forEach { schema ->
            assertThat(persistence.upsertSql(schema)).contains("INSERT INTO $schema.outbox_events")
            assertThat(persistence.selectSql(schema)).contains("FROM $schema.outbox_events")
            assertThat(persistence.selectSql(schema)).contains("FOR UPDATE SKIP LOCKED")
        }

        assertThatThrownBy { persistence.upsertSql("orders; DROP TABLE orders.orders") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { persistence.selectSql("public") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
