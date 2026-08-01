package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.orderservice.model.OrderCompensation
import com.pawsnearme.orderservice.repository.OrderCompensationRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class OrderCompensationService(
    private val repository: OrderCompensationRepository,
    private val objectMapper: ObjectMapper,
    private val catalogModule: CatalogModuleApi,
    private val paymentModule: PaymentModuleApi
) {
    data class Item(val offeringId: UUID, val quantity: Int)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(orderId: UUID?, customerId: UUID, couponCode: String?, items: List<OrderItemRequest>) {
        if (items.isEmpty() && couponCode.isNullOrBlank()) return
        repository.saveAndFlush(
            OrderCompensation(
                orderId = orderId,
                customerId = customerId,
                couponCode = couponCode?.trim()?.uppercase(),
                payloadJson = objectMapper.writeValueAsString(items.map { Item(it.offeringId, it.quantity) })
            )
        )
    }

    @Scheduled(fixedDelayString = "\${order.compensation.poll-delay-ms:5000}")
    @SchedulerLock(name = "orderCompensationWorker", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    fun runPending() {
        repository.findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            listOf("PENDING", "RETRY"), Instant.now()
        ).forEach(::processOne)
    }

    @Transactional
    fun processOne(compensation: OrderCompensation) {
        compensation.status = "PROCESSING"
        compensation.updatedAt = Instant.now()
        repository.saveAndFlush(compensation)
        try {
            val items: List<Item> = objectMapper.readValue(
                compensation.payloadJson, object : TypeReference<List<Item>>() {}
            )
            items.forEach { item ->
                val keyMaterial = "compensate:${compensation.compensationId}:${item.offeringId}:${item.quantity}"
                catalogModule.restoreStock(
                    StockMutationCommand(
                        offeringId = item.offeringId,
                        quantity = item.quantity,
                        idempotencyKey = UUID.nameUUIDFromBytes(keyMaterial.toByteArray())
                    )
                )
            }
            val coupon = compensation.couponCode
            val orderId = compensation.orderId
            if (!coupon.isNullOrBlank() && orderId != null) {
                paymentModule.releaseCoupon(coupon, compensation.customerId, orderId)
            }
            compensation.status = "COMPENSATED"
            compensation.lastError = null
        } catch (error: Exception) {
            compensation.attemptCount += 1
            compensation.lastError = error.message?.take(4000)
            compensation.status = if (compensation.attemptCount >= 12) "FAILED" else "RETRY"
            val delay = Duration.ofSeconds((1L shl compensation.attemptCount.coerceAtMost(8)).coerceAtMost(300))
            compensation.nextAttemptAt = Instant.now().plus(delay)
            logger.error("Compensation {} failed on attempt {}", compensation.compensationId, compensation.attemptCount, error)
        }
        compensation.updatedAt = Instant.now()
        repository.save(compensation)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OrderCompensationService::class.java)
    }
}
