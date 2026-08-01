package com.pawsnearme.application.scheduling

import com.pawsnearme.common.scheduling.MyPetSchedulerCatalog
import com.pawsnearme.common.scheduling.SchedulerCadenceKind
import com.pawsnearme.common.scheduling.SchedulerJobCatalog
import com.pawsnearme.common.scheduling.SchedulerRuntimeRole
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration(proxyBeanMethods = false)
class SchedulerRuntimeConfiguration {
    @Bean
    fun schedulerJobCatalog(): SchedulerJobCatalog = MyPetSchedulerCatalog.create()

    @Bean
    fun schedulerRuntimeInfoContributor(
        catalog: SchedulerJobCatalog,
        environment: Environment
    ): InfoContributor = InfoContributor { builder ->
        val role = SchedulerRuntimeRole.parse(environment.getProperty("mypet.scheduling.role", "API"))
        val lockTables = catalog.jobs.map { it.lockTable }.distinct().sorted()
        builder.withDetail(
            "schedulerRuntime",
            linkedMapOf(
                "milestone" to "M7",
                "role" to role.name,
                "workersEnabled" to role.executesWorkers,
                "jobCount" to catalog.jobs.size,
                "ownerCount" to catalog.ownerModules.size,
                "fixedDelayJobCount" to catalog.jobs.count { it.cadenceKind == SchedulerCadenceKind.FIXED_DELAY },
                "cronJobCount" to catalog.jobs.count { it.cadenceKind == SchedulerCadenceKind.CRON },
                "lockProvider" to "shared-jdbc-db-time",
                "lockTables" to lockTables,
                "apiWorkerSplitSupported" to true,
                "profiles" to listOf("api", "worker"),
                "legacyDefault" to "ALL",
                "jobs" to catalog.jobs.map { job ->
                    linkedMapOf(
                        "id" to job.id,
                        "owner" to job.ownerModule,
                        "component" to job.component,
                        "method" to job.method,
                        "cadenceKind" to job.cadenceKind.name,
                        "cadence" to job.cadence,
                        "lockIdentity" to job.lockIdentity
                    )
                }
            )
        )
    }
}
