package com.pawsnearme.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication

/**
 * Single deployable shell for the MyPet modular-monolith migration.
 *
 * M2 links business modules as explicit, non-transitive libraries. M3 adds the
 * servlet-native security and API-edge boundary. M4 adds one application-owned
 * database connection and isolated Flyway orchestration while retaining every
 * legacy schema and migration-history table. Automatic datasource and Flyway
 * configuration are disabled so the shell still starts without PostgreSQL when
 * the M4 database boundary is not explicitly enabled.
 */
@SpringBootApplication(
    exclude = [
        DataSourceAutoConfiguration::class,
        FlywayAutoConfiguration::class
    ]
)
class MyPetApplication

fun main(args: Array<String>) {
    runApplication<MyPetApplication>(*args)
}
