package com.pawsnearme.application.database

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mypet.database")
class DatabaseConsolidationProperties {
    var enabled: Boolean = false
    var url: String = ""
    var username: String = ""
    var password: String = ""
    var maximumPoolSize: Int = 5
    var minimumIdle: Int = 1
    var connectionTimeoutMs: Long = 5_000
    var migrateOnStartup: Boolean = true
    var connectRetries: Int = 10
    var lockRetryCount: Int = 50

    fun validateEnabledConfiguration() {
        require(url.isNotBlank()) {
            "MYPET_DATABASE_ENABLED=true requires MYPET_DB_URL"
        }
        require(username.isNotBlank()) {
            "MYPET_DATABASE_ENABLED=true requires MYPET_DB_USERNAME"
        }
        require(maximumPoolSize > 0) { "MYPET_DB_POOL_MAX_SIZE must be positive" }
        require(minimumIdle >= 0) { "MYPET_DB_POOL_MIN_IDLE must not be negative" }
        require(minimumIdle <= maximumPoolSize) {
            "MYPET_DB_POOL_MIN_IDLE must not exceed MYPET_DB_POOL_MAX_SIZE"
        }
        require(connectionTimeoutMs >= 250) {
            "MYPET_DB_CONNECTION_TIMEOUT_MS must be at least 250"
        }
        require(connectRetries >= 0) { "MYPET_FLYWAY_CONNECT_RETRIES must not be negative" }
        require(lockRetryCount >= 0) { "MYPET_FLYWAY_LOCK_RETRY_COUNT must not be negative" }
    }
}
