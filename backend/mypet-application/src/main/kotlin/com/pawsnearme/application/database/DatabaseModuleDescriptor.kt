package com.pawsnearme.application.database

/**
 * Database migration identity retained from the legacy distributed services.
 *
 * M4 intentionally reuses each module's existing schema and Flyway history
 * table. Migration filenames remain owned by the original service projects.
 */
data class DatabaseModuleDescriptor(
    val id: String,
    val schema: String,
    val historyTable: String,
    val location: String
)

object DatabaseModuleRegistry {
    val modules: List<DatabaseModuleDescriptor> = listOf(
        DatabaseModuleDescriptor(
            id = "provider",
            schema = "providers",
            historyTable = "flyway_schema_history_provider",
            location = "classpath:db/migration/provider"
        ),
        DatabaseModuleDescriptor(
            id = "catalog",
            schema = "catalog",
            historyTable = "flyway_schema_history_catalog",
            location = "classpath:db/migration/catalog"
        ),
        DatabaseModuleDescriptor(
            id = "discovery",
            schema = "providers",
            historyTable = "flyway_schema_history_discovery",
            location = "classpath:db/migration/discovery"
        ),
        DatabaseModuleDescriptor(
            id = "order",
            schema = "orders",
            historyTable = "flyway_schema_history_order",
            location = "classpath:db/migration/order"
        ),
        DatabaseModuleDescriptor(
            id = "appointment",
            schema = "appointments",
            historyTable = "flyway_schema_history_appointment",
            location = "classpath:db/migration/appointment"
        ),
        DatabaseModuleDescriptor(
            id = "dispatch",
            schema = "dispatch",
            historyTable = "flyway_schema_history_dispatch",
            location = "classpath:db/migration/dispatch"
        ),
        DatabaseModuleDescriptor(
            id = "captain",
            schema = "captains",
            historyTable = "flyway_schema_history_captain",
            location = "classpath:db/migration/captain"
        ),
        DatabaseModuleDescriptor(
            id = "notification",
            schema = "notifications",
            historyTable = "flyway_schema_history_notification",
            location = "classpath:db/migration/notification"
        ),
        DatabaseModuleDescriptor(
            id = "review",
            schema = "reviews",
            historyTable = "flyway_schema_history_review",
            location = "classpath:db/migration/review"
        ),
        DatabaseModuleDescriptor(
            id = "payment",
            schema = "payments",
            historyTable = "flyway_schema_history_payment",
            location = "classpath:db/migration/payment"
        ),
        DatabaseModuleDescriptor(
            id = "chat",
            schema = "chat",
            historyTable = "flyway_schema_history_chat",
            location = "classpath:db/migration/chat"
        ),
        DatabaseModuleDescriptor(
            id = "content",
            schema = "content",
            historyTable = "flyway_schema_history_content",
            location = "classpath:db/migration/content"
        )
    )

    init {
        require(modules.size == 12) { "M4 must retain all twelve business migration owners" }
        require(modules.map(DatabaseModuleDescriptor::id).distinct().size == modules.size) {
            "Database module ids must be unique"
        }
        require(modules.map(DatabaseModuleDescriptor::historyTable).distinct().size == modules.size) {
            "Flyway history tables must remain isolated per module"
        }
        require(modules.map(DatabaseModuleDescriptor::location).distinct().size == modules.size) {
            "Flyway migration locations must be namespaced per module"
        }
        require(modules.all { it.location == "classpath:db/migration/${it.id}" }) {
            "Every module must use its namespaced application migration location"
        }
    }
}
