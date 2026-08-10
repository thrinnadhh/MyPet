package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class OrderReleaseReconciliationService(
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val catalogModule: CatalogModuleApi,
    private val paymentModule: PaymentModuleApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["orders.events"], groupId = "order-release-reconciliation")
    fun onOrderEvent(message: String) {
        handlePayload(message)
    }

    @Transactional
    fun handlePayload(message: String) {
        val node = runCatching { objectMapper.readTree(message) }.getOrElse { error ->
            logger.warn("Ignoring malformed order financial event", error)
            return
        }
        val eventType = node.path("eventType").asText()
        if (eventType !in setOf("OrderCancelled", "OrderStatusChanged")) return
        val toStatus = node.path("toStatus").asText()
        val orderId = runCatching { UUID.fromString(node.path("orderId").asText()) }.getOrNull() ?: return
        when (toStatus) {
            OrderStatus.CANCELLED.name, OrderStatus.REJECTED.name -> reconcileRelease(orderId)
            OrderStatus.DELIVERED.name -> settleDelivered(orderId)
        }
    }

    @Transactional
    fun reconcileRelease(orderId: UUID) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        if (order.status !in setOf(OrderStatus.CANCELLED, OrderStatus.REJECTED)) return

        orderItemRepository.findByOrderId(orderId).forEach { item ->
            val itemId = requireNotNull(item.orderItemId) { "Order item ID is required for stock restoration" }
            catalogModule.restoreStock(
                StockMutationCommand(
                    offeringId = item.offeringId,
                    quantity = item.quantity,
                    idempotencyKey = UUID.nameUUIDFromBytes(
                        "RESTORE:$orderId:$itemId".toByteArray(StandardCharsets.UTF_8)
                    ),
                )
            )
        }

        order.couponCode?.let { code ->
            paymentModule.releaseCoupon(code, order.customerId, orderId)
        }
        order.loyaltyRewardId?.let { rewardId ->
            paymentModule.releaseLoyaltyReward(rewardId, order.customerId, orderId)
        }

        when (order.paymentStatus) {
            PaymentStatus.PENDING -> paymentModule.expireOrderPayment(
                orderId,
                "Order ${order.status.name.lowercase()} before online payment completed",
            )
            PaymentStatus.SUCCESS -> {
                order.paymentStatus = PaymentStatus.REFUND_PENDING
                orderRepository.saveAndFlush(order)
                paymentModule.refundOrder(orderId)
            }
            else -> Unit
        }
    }

    @Transactional
    fun settleDelivered(orderId: UUID) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        if (order.status != OrderStatus.DELIVERED) return
        order.loyaltyRewardId?.let { rewardId ->
            paymentModule.redeemLoyaltyReward(rewardId, order.customerId, orderId)
        }
    }
}
