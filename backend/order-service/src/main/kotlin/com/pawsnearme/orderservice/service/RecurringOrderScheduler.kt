package com.pawsnearme.orderservice.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RecurringOrderScheduler(
    private val recurringOrderService: RecurringOrderService
) {
    @Scheduled(cron = "${'$'}{order.recurring-reminder-cron:0 0 * * * *}")
    @SchedulerLock(
        name = "recurringOrderConfirmationReminder",
        lockAtMostFor = "${'$'}{order.recurring-reminder-lock-at-most-for:PT55M}",
        lockAtLeastFor = "${'$'}{order.recurring-reminder-lock-at-least-for:PT1M}"
    )
    fun requestDueConfirmations() {
        recurringOrderService.markDueForConfirmation()
    }
}
