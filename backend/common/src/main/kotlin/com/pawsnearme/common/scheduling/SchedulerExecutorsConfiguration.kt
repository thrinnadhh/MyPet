package com.pawsnearme.common.scheduling

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * Provides bounded scheduler executors for service and modular-monolith runtimes.
 *
 * Durable outbox delivery is intentionally isolated from general housekeeping
 * workers so a slow reminder, cleanup or compensation task cannot block event
 * publication. API-only runtimes still remove Spring's scheduled-method
 * processor through [SchedulerRoleBeanDefinitionPostProcessor].
 */
@Configuration(proxyBeanMethods = false)
class SchedulerExecutorsConfiguration {

    @Bean(name = ["taskScheduler"])
    fun taskScheduler(
        @Value("\${mypet.scheduling.pool-size:8}") poolSize: Int,
    ): ThreadPoolTaskScheduler = createScheduler(
        poolSize = poolSize,
        threadNamePrefix = "mypet-scheduler-",
    )

    @Bean(name = ["outboxTaskScheduler"])
    fun outboxTaskScheduler(
        @Value("\${mypet.scheduling.outbox-pool-size:2}") poolSize: Int,
    ): ThreadPoolTaskScheduler = createScheduler(
        poolSize = poolSize,
        threadNamePrefix = "mypet-outbox-",
    )

    private fun createScheduler(
        poolSize: Int,
        threadNamePrefix: String,
    ): ThreadPoolTaskScheduler = ThreadPoolTaskScheduler().apply {
        setPoolSize(poolSize.coerceAtLeast(1))
        setThreadNamePrefix(threadNamePrefix)
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
        setRemoveOnCancelPolicy(true)
    }
}
