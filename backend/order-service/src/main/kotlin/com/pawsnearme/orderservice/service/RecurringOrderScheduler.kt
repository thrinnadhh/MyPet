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
        lockAtMostFor = "PT55M",
        lockAtLeastFor = "PT1M"
    )
    fun requestDueConfirmations() {
        recurringOrderService.markDueForConfirmation()
    }
}
