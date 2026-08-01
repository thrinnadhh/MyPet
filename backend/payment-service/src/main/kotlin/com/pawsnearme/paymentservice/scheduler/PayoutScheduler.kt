package com.pawsnearme.paymentservice.scheduler

import com.pawsnearme.common.scheduling.WorkerScheduler
import com.pawsnearme.paymentservice.service.PaymentService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@WorkerScheduler
class PayoutScheduler(private val paymentService: PaymentService) {
    private val logger = LoggerFactory.getLogger(PayoutScheduler::class.java)

    @Scheduled(cron = "\${payout.scheduler.cron:0 0 0 * * MON}")
    @SchedulerLock(name = "paymentPayoutScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    fun scheduleWeeklyPayouts() {
        val today = LocalDate.now()
        val end = today.minusDays(1)
        val start = end.minusDays(6)

        logger.info("Executing scheduled weekly payouts calculation for period: {} to {}", start, end)
        try {
            val payouts = paymentService.calculatePayouts(start, end)
            logger.info("Successfully completed weekly payouts calculation. Created {} payouts.", payouts.size)
        } catch (e: Exception) {
            logger.error("Failed to execute scheduled weekly payouts calculation", e)
        }
    }
}
