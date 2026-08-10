package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.repository.TransactionRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class PendingPaymentOrderWorker(
    private val transactionRepository: TransactionRepository,
    private val lifecycleService: OrderPaymentLifecycleService,
    @Value("\${payment.order.pending-timeout:PT30M}") private val pendingTimeout: Duration = Duration.ofMinutes(30),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payment.order.pending-scan-ms:60000}")
    @SchedulerLock(name = "pendingPaymentOrderWorker", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    fun run() {
        val cutoff = Instant.now().minus(pendingTimeout)
        transactionRepository.findTop100ByTransactionTypeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            "ORDER_PAYMENT",
            "PENDING",
            cutoff,
        ).forEach { transaction ->
            runCatching {
                lifecycleService.fail(
                    transaction,
                    "Online payment was not completed within ${pendingTimeout.toMinutes()} minutes",
                    "PaymentExpired",
                )
            }.onFailure { error ->
                logger.error("Failed to expire pending payment {}", transaction.transactionId, error)
            }
        }
    }
}
