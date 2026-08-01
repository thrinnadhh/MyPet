package com.pawsnearme.application.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

class DatabaseModuleRegistryTest {

    private val resolver = PathMatchingResourcePatternResolver()

    @Test
    fun `registry preserves all legacy migration owners and history tables`() {
        assertEquals(
            listOf(
                "provider" to ("providers" to "flyway_schema_history_provider"),
                "catalog" to ("catalog" to "flyway_schema_history_catalog"),
                "discovery" to ("providers" to "flyway_schema_history_discovery"),
                "order" to ("orders" to "flyway_schema_history_order"),
                "appointment" to ("appointments" to "flyway_schema_history_appointment"),
                "dispatch" to ("dispatch" to "flyway_schema_history_dispatch"),
                "captain" to ("captains" to "flyway_schema_history_captain"),
                "notification" to ("notifications" to "flyway_schema_history_notification"),
                "review" to ("reviews" to "flyway_schema_history_review"),
                "payment" to ("payments" to "flyway_schema_history_payment"),
                "chat" to ("chat" to "flyway_schema_history_chat"),
                "content" to ("content" to "flyway_schema_history_content")
            ),
            DatabaseModuleRegistry.modules.map { descriptor ->
                descriptor.id to (descriptor.schema to descriptor.historyTable)
            }
        )
    }

    @Test
    fun `every module has a namespaced packaged migration bundle`() {
        val validName = Regex("(?:V[^_]+__.+|R__.+)\\.sql")

        DatabaseModuleRegistry.modules.forEach { descriptor ->
            val pattern = descriptor.location.replace("classpath:", "classpath*:") + "/*.sql"
            val resources = resolver.getResources(pattern).toList()

            assertTrue(resources.isNotEmpty(), "${descriptor.id} has no packaged migrations")
            assertTrue(
                resources.all { resource ->
                    resource.filename?.matches(validName) == true
                },
                "${descriptor.id} contains a malformed packaged migration"
            )
        }
    }

    @Test
    fun `enabled properties reject missing connection details`() {
        val properties = DatabaseConsolidationProperties().apply {
            enabled = true
        }

        assertThrows<IllegalArgumentException> {
            properties.validateEnabledConfiguration()
        }
    }
}
