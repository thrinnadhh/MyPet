package com.pawsnearme.application.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

enum class DatabaseMigrationPhase {
    DISABLED,
    PENDING,
    MIGRATING,
    READY,
    FAILED
}

data class ModuleMigrationStatus(
    val moduleId: String,
    val schema: String,
    val historyTable: String,
    val migrationsExecuted: Int,
    val currentVersion: String
)

class DatabaseMigrationState(enabled: Boolean) {
    @Volatile
    var phase: DatabaseMigrationPhase =
        if (enabled) DatabaseMigrationPhase.PENDING else DatabaseMigrationPhase.DISABLED
        private set

    @Volatile
    var failure: String? = null
        private set

    private val moduleStatuses = ConcurrentHashMap<String, ModuleMigrationStatus>()

    fun markMigrating() {
        phase = DatabaseMigrationPhase.MIGRATING
        failure = null
    }

    fun record(status: ModuleMigrationStatus) {
        moduleStatuses[status.moduleId] = status
    }

    fun markReady() {
        phase = DatabaseMigrationPhase.READY
    }

    fun markFailed(error: Throwable) {
        phase = DatabaseMigrationPhase.FAILED
        failure = error.message ?: error.javaClass.simpleName
    }

    fun modules(): List<ModuleMigrationStatus> = moduleStatuses.values.sortedBy { it.moduleId }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseConsolidationProperties::class)
class DatabaseConsolidationMetadataConfiguration {

    @Bean
    fun databaseMigrationState(
        properties: DatabaseConsolidationProperties
    ): DatabaseMigrationState = DatabaseMigrationState(properties.enabled)

    @Bean
    fun databaseConsolidationInfoContributor(
        properties: DatabaseConsolidationProperties,
        state: DatabaseMigrationState
    ): InfoContributor = object : InfoContributor {
        override fun contribute(builder: Info.Builder) {
            builder.withDetail(
                "databaseConsolidation",
                mapOf(
                    "enabled" to properties.enabled,
                    "mode" to if (properties.enabled) "application-owned" else "shadow-ready",
                    "phase" to state.phase.name,
                    "moduleCount" to DatabaseModuleRegistry.modules.size,
                    "migrationOwners" to DatabaseModuleRegistry.modules.map { module ->
                        mapOf(
                            "id" to module.id,
                            "schema" to module.schema,
                            "historyTable" to module.historyTable
                        )
                    },
                    "modules" to state.modules().map { module ->
                        mapOf(
                            "id" to module.moduleId,
                            "schema" to module.schema,
                            "historyTable" to module.historyTable,
                            "migrationsExecuted" to module.migrationsExecuted,
                            "currentVersion" to module.currentVersion
                        )
                    }
                )
            )
        }
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mypet.database", name = ["enabled"], havingValue = "true")
class EnabledDatabaseConsolidationConfiguration(
    private val properties: DatabaseConsolidationProperties
) {

    @Bean(destroyMethod = "close")
    fun consolidatedDataSource(): HikariDataSource {
        properties.validateEnabledConfiguration()

        val config = HikariConfig().apply {
            jdbcUrl = properties.url
            username = properties.username
            password = properties.password
            driverClassName = "org.postgresql.Driver"
            poolName = "mypet-consolidated-database"
            maximumPoolSize = properties.maximumPoolSize
            minimumIdle = properties.minimumIdle
            connectionTimeout = properties.connectionTimeoutMs
            validationTimeout = properties.connectionTimeoutMs.coerceAtMost(5_000)
            initializationFailTimeout = properties.connectionTimeoutMs
            isAutoCommit = true
        }
        return HikariDataSource(config)
    }

    @Bean
    fun databaseMigrationOrchestrator(
        dataSource: DataSource,
        state: DatabaseMigrationState
    ): DatabaseMigrationOrchestrator = DatabaseMigrationOrchestrator(
        dataSource = dataSource,
        properties = properties,
        state = state
    )

    @Bean
    fun databaseConsolidationHealthIndicator(
        state: DatabaseMigrationState
    ): HealthIndicator = HealthIndicator {
        when (state.phase) {
            DatabaseMigrationPhase.READY -> Health.up()
                .withDetail("phase", state.phase.name)
                .withDetail("modules", state.modules().size)
                .build()

            DatabaseMigrationPhase.FAILED -> Health.down()
                .withDetail("phase", state.phase.name)
                .withDetail("error", state.failure ?: "unknown")
                .build()

            else -> Health.outOfService()
                .withDetail("phase", state.phase.name)
                .build()
        }
    }
}

class DatabaseMigrationOrchestrator(
    private val dataSource: DataSource,
    private val properties: DatabaseConsolidationProperties,
    private val state: DatabaseMigrationState
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        state.markMigrating()
        try {
            DatabaseModuleRegistry.modules.forEach { descriptor ->
                migrateModule(descriptor)
            }
            state.markReady()
        } catch (error: Throwable) {
            state.markFailed(error)
            throw error
        }
    }

    private fun migrateModule(descriptor: DatabaseModuleDescriptor) {
        val flyway = Flyway.configure(javaClass.classLoader)
            .dataSource(dataSource)
            .locations(descriptor.location)
            .schemas(descriptor.schema)
            .defaultSchema(descriptor.schema)
            .table(descriptor.historyTable)
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .validateMigrationNaming(true)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .outOfOrder(false)
            .createSchemas(false)
            .connectRetries(properties.connectRetries)
            .lockRetryCount(properties.lockRetryCount)
            .failOnMissingLocations(true)
            .load()

        val migrationCount = if (properties.migrateOnStartup) {
            flyway.migrate().migrationsExecuted
        } else {
            val validation = flyway.validateWithResult()
            check(validation.validationSuccessful) {
                "Flyway validation failed for ${descriptor.id}"
            }
            0
        }

        val validation = flyway.validateWithResult()
        check(validation.validationSuccessful) {
            "Flyway post-migration validation failed for ${descriptor.id}"
        }

        state.record(
            ModuleMigrationStatus(
                moduleId = descriptor.id,
                schema = descriptor.schema,
                historyTable = descriptor.historyTable,
                migrationsExecuted = migrationCount,
                currentVersion = flyway.info().current()?.version?.version ?: "none"
            )
        )
    }
}
