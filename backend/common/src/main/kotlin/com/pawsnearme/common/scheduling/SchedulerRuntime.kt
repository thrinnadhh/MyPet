package com.pawsnearme.common.scheduling

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

enum class SchedulerRuntimeRole {
    ALL,
    API,
    WORKER,
    DISABLED;

    val executesWorkers: Boolean
        get() = this == ALL || this == WORKER

    companion object {
        fun parse(value: String?): SchedulerRuntimeRole = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        } ?: ALL
    }
}

/**
 * Activates a scheduled worker only when this process owns background work.
 *
 * Existing standalone services default to ALL, preserving production behavior.
 * API-only deployments set `mypet.scheduling.role=API`; dedicated workers use
 * `WORKER`. DISABLED is reserved for diagnostics and migration rollback.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(WorkerSchedulerCondition::class)
annotation class WorkerScheduler

class WorkerSchedulerCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        SchedulerRuntimeRole.parse(
            context.environment.getProperty("mypet.scheduling.role")
        ).executesWorkers
}

enum class SchedulerCadenceKind {
    FIXED_DELAY,
    CRON
}

data class SchedulerJobDescriptor(
    val id: String,
    val ownerModule: String,
    val component: String,
    val method: String,
    val cadenceKind: SchedulerCadenceKind,
    val cadence: String,
    val lockTable: String,
    val lockName: String,
    val workerEligible: Boolean = true
) {
    val lockIdentity: String = "$lockTable/$lockName"

    init {
        require(id.matches(Regex("[a-z][a-z0-9.-]*"))) { "Invalid scheduler job id: $id" }
        require(ownerModule.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid scheduler owner: $ownerModule" }
        require(lockTable.matches(Regex("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*"))) {
            "Scheduler lock table must be schema-qualified: $lockTable"
        }
        require(lockName.isNotBlank()) { "Scheduler lock name cannot be blank" }
        require(cadence.isNotBlank()) { "Scheduler cadence cannot be blank" }
    }
}

class SchedulerJobCatalog(descriptors: Collection<SchedulerJobDescriptor>) {
    val jobs: List<SchedulerJobDescriptor> = descriptors.sortedBy(SchedulerJobDescriptor::id)
    val ownerModules: Set<String> = jobs.mapTo(sortedSetOf(), SchedulerJobDescriptor::ownerModule)

    init {
        require(jobs.isNotEmpty()) { "At least one scheduled job is required" }
        require(jobs.map(SchedulerJobDescriptor::id).distinct().size == jobs.size) {
            "Scheduler job ids must be unique"
        }
        require(jobs.map(SchedulerJobDescriptor::lockIdentity).distinct().size == jobs.size) {
            "Scheduler lock identities must be unique"
        }
        require(jobs.all(SchedulerJobDescriptor::workerEligible)) {
            "Every M7 scheduled job must support worker execution"
        }
    }

    fun jobsOwnedBy(module: String): List<SchedulerJobDescriptor> = jobs.filter { it.ownerModule == module }
}

object MyPetSchedulerCatalog {
    fun create(): SchedulerJobCatalog = SchedulerJobCatalog(
        listOf(
            outbox("order", "orders.shedlock"),
            outbox("appointment", "appointments.shedlock"),
            outbox("dispatch", "dispatch.shedlock"),
            outbox("provider", "providers.shedlock"),
            outbox("review", "reviews.shedlock"),
            outbox("payment", "payments.shedlock"),
            SchedulerJobDescriptor(
                id = "order.complete-delivered",
                ownerModule = "order",
                component = "OrderCompletionWorker",
                method = "completeDeliveredOrders",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "PT1M",
                lockTable = "orders.shedlock",
                lockName = "order_completeDeliveredOrders"
            ),
            SchedulerJobDescriptor(
                id = "order.compensation",
                ownerModule = "order",
                component = "OrderCompensationWorker",
                method = "runPending",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "${'$'}{order.compensation.poll-delay-ms:5000}ms",
                lockTable = "orders.shedlock",
                lockName = "orderCompensationWorker"
            ),
            SchedulerJobDescriptor(
                id = "appointment.expire-holds",
                ownerModule = "appointment",
                component = "AppointmentHoldCleanupWorker",
                method = "cleanupExpiredHolds",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "PT5S",
                lockTable = "appointments.shedlock",
                lockName = "appointment_cleanupExpiredHolds"
            ),
            SchedulerJobDescriptor(
                id = "dispatch.offer-timeouts",
                ownerModule = "dispatch",
                component = "DispatchOfferTimeoutWorker",
                method = "checkOfferTimeouts",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "PT5S",
                lockTable = "dispatch.shedlock",
                lockName = "dispatch_checkOfferTimeouts"
            ),
            SchedulerJobDescriptor(
                id = "notification.dispatch-reminders",
                ownerModule = "notification",
                component = "ReminderDispatchWorker",
                method = "dispatchDueReminders",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "PT5S",
                lockTable = "notifications.shedlock",
                lockName = "notification-dispatch-due-reminders"
            ),
            SchedulerJobDescriptor(
                id = "notification.sync-vaccination-reminders",
                ownerModule = "notification",
                component = "VaccinationReminderSyncWorker",
                method = "syncEnabledReminders",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "PT1H;initial=PT30S",
                lockTable = "notifications.shedlock",
                lockName = "notification-sync-vaccination-reminders"
            ),
            SchedulerJobDescriptor(
                id = "content.close-banner-auctions",
                ownerModule = "content",
                component = "BannerAuctionWorker",
                method = "closeExpiredAuctions",
                cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
                cadence = "PT10S",
                lockTable = "content.shedlock",
                lockName = "content-close-expired-banner-auctions"
            ),
            SchedulerJobDescriptor(
                id = "payment.weekly-payouts",
                ownerModule = "payment",
                component = "PayoutScheduler",
                method = "scheduleWeeklyPayouts",
                cadenceKind = SchedulerCadenceKind.CRON,
                cadence = "${'$'}{payout.scheduler.cron:0 0 0 * * MON}",
                lockTable = "payments.shedlock",
                lockName = "paymentPayoutScheduler"
            )
        )
    )

    private fun outbox(owner: String, table: String) = SchedulerJobDescriptor(
        id = "$owner.outbox-publish",
        ownerModule = owner,
        component = "OutboxPoller",
        method = "pollAndPublish",
        cadenceKind = SchedulerCadenceKind.FIXED_DELAY,
        cadence = "PT1S",
        lockTable = table,
        lockName = "outbox_pollAndPublish"
    )
}

object SchedulerLockProviderFactory {
    private val tablePattern = Regex("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*")

    fun create(dataSource: DataSource, tableName: String): LockProvider {
        require(tableName.matches(tablePattern)) {
            "ShedLock table must be a safe schema-qualified identifier"
        }
        return JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .withTableName(tableName)
                .usingDbTime()
                .build()
        )
    }
}
