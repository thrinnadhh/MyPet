package com.pawsnearme.dispatchservice.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * Distributed lock for @Scheduled tasks.
 *
 * Without this, every replica fires every scheduler concurrently, causing:
 *  - Duplicate Kafka events from OutboxPoller
 *  - Double-expired dispatch offers from checkOfferTimeouts
 *
 * ShedLock uses the shared PostgreSQL DB (shedlock table, auto-created)
 * as the lock store — no extra infrastructure required.
 *
 * lockAtMostFor: Maximum time a lock is held even if the owning node crashes.
 * lockAtLeastFor: Minimum lock duration to prevent rapid re-entry from fast-completing tasks.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
class ShedLockConfig {

    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        )
}
