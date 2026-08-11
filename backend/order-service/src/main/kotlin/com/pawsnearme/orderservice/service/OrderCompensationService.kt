package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.orderservice.model.OrderCompensation
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.repository.OrderCompensationRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
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
    data class Item(val offeringId: UUID, val quantity: Int, val orderItemId: UUID? = null)

    /** Compatibility entry point used by older order paths. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(orderId: UUID?, customerId: UUID, couponCode: String?, items: List<OrderItemRequest>) {
        record(
            orderId = orderId,
            customerId = customerId,
            couponCode = couponCode,
            loyaltyRewardId = null,
            paymentPrepared = false,
            items = items.map { Item(it.offeringId, it.quantity) },
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordCheckoutFailure(
        orderId: UUID,
        customerId: UUID,
        couponCode: String?,
        loyaltyRewardId: UUID?,
        paymentPrepared: Boolean,
        items: List<OrderItem>,
    ) {
        record(
            orderId = orderId,
            customerId = customerId,
            couponCode = couponCode,
            loyaltyRewardId = loyaltyRewardId,
            paymentPrepared = paymentPrepared,
            items = items.map { item ->
                Item(
                    offeringId = item.offeringId,
                    quantity = item.quantity,
                    orderItemId = item.orderItemId,
                )
            },
        )
    }

    private fun record(
        orderId: UUID?,
        customerId: UUID,
        couponCode: String?,
        loyaltyRewardId: UUID?,
        paymentPrepared: Boolean,
        items: List<Item>,
    ) {
        if (items.isEmpty() && couponCode.isNullOrBlank() && loyaltyRewardId == null && !paymentPrepared) return
        repository.saveAndFlush(
            OrderCompensation(
                orderId = orderId,
                customerId = customerId,
                couponCode = couponCode?.trim()?.uppercase(),
                loyaltyRewardId = loyaltyRewardId,
                paymentPrepared = paymentPrepared,
                payloadJson = objectMapper.writeValueAsString(items),
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
                val operation = if (compensation.orderId != null && item.orderItemId != null) {
                    "RESTORE:${compensation.orderId}:${item.orderItemId}"
                } else {
                    "COMPENSATE:${compensation.compensationId}:${item.offeringId}:${item.quantity}"
                }
                catalogModule.restoreStock(
                    StockMutationCommand(
                        offeringId = item.offeringId,
                        quantity = item.quantity,
                        idempotencyKey = UUID.nameUUIDFromBytes(operation.toByteArray(StandardCharsets.UTF_8)),
                    )
                )
            }
            val orderId = compensation.orderId
            if (orderId != null) {
                compensation.couponCode?.let { coupon ->
                    paymentModule.releaseCoupon(coupon, compensation.customerId, orderId)
                }
                compensation.loyaltyRewardId?.let { rewardId ->
                    paymentModule.releaseLoyaltyReward(rewardId, compensation.customerId, orderId)
                }
                if (compensation.paymentPrepared) {
                    paymentModule.expireOrderPayment(orderId, "Checkout compensation expired an incomplete payment")
                }
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