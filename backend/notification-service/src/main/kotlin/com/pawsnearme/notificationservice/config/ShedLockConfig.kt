package com.pawsnearme.notificationservice.config

import com.pawsnearme.common.scheduling.SchedulerLockProviderFactory
import com.pawsnearme.common.scheduling.SchedulerRuntimeInfrastructureConfiguration
import com.pawsnearme.common.scheduling.WorkerScheduler
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import javax.sql.DataSource

@Configuration
@Import(SchedulerRuntimeInfrastructureConfiguration::class)
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class ShedLockConfig {
    @Bean
    @WorkerScheduler
    fun lockProvider(
        dataSource: DataSource,
        @Value("\${mypet.scheduling.lock-table:notifications.shedlock}") tableName: String
    ): LockProvider = SchedulerLockProviderFactory.create(dataSource, tableName)
}
