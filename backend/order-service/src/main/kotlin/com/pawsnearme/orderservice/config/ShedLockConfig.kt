package com.pawsnearme.orderservice.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * Distributed scheduler lock for order-service.
 *
 * Without this, when multiple replicas are running, OrderCompletionWorker
 * fires on every instance simultaneously — causing each delivered order
 * to be auto-completed N times per minute (one per replica).
 *
 * Uses the shared PostgreSQL DB (shedlock table) as the lock store.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
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
