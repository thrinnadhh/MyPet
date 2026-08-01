package com.pawsnearme.notificationservice.config

import com.pawsnearme.common.scheduling.SchedulerLockProviderFactory
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class ShedLockConfig {
    @Bean
    fun lockProvider(
        dataSource: DataSource,
        @Value("\${mypet.scheduling.lock-table:notifications.shedlock}") tableName: String
    ): LockProvider = SchedulerLockProviderFactory.create(dataSource, tableName)
}
