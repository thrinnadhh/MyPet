package com.pawsnearme.paymentservice.scheduler

import com.pawsnearme.paymentservice.service.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PayoutScheduler(private val paymentService: PaymentService) {
    private val logger = LoggerFactory.getLogger(PayoutScheduler::class.java)

    @Scheduled(cron = "\${payout.scheduler.cron:0 0 0 * * MON}")
    fun scheduleWeeklyPayouts() {
        val today = LocalDate.now()
        val end = today.minusDays(1) // Previous Sunday
        val start = end.minusDays(6) // Previous Monday
        
        logger.info("Executing scheduled weekly payouts calculation for period: {} to {}", start, end)
        try {
            val payouts = paymentService.calculatePayouts(start, end)
            logger.info("Successfully completed weekly payouts calculation. Created {} payouts.", payouts.size)
        } catch (e: Exception) {
            logger.error("Failed to execute scheduled weekly payouts calculation", e)
        }
    }
}
