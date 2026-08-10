package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.orderservice.model.OrderActor
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class OrderPaymentLifecycleEvent(
    val eventId: UUID,
    val eventType: String,
    val transactionId: UUID,
    val referenceId: UUID,
    val transactionType: String,
    val actorId: UUID,
    val amount: BigDecimal,
    val gateway: String,
    val gatewayTransactionId: String? = null,
    val reason: String? = null,
)

@Service
class OrderPaymentEventListener(
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val paymentModule: PaymentModuleApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["payments.events"], groupId = "order-service-payments")
    fun onPaymentEvent(message: String) {
        handlePayload(message)
    }

    @Transactional
    fun handlePayload(message: String) {
        val event = runCatching { objectMapper.readValue(message, OrderPaymentLifecycleEvent::class.java) }
            .getOrElse { error ->
                logger.warn("Ignoring malformed payment event", error)
                return
            }
        if (event.transactionType != "ORDER_PAYMENT") return
        val order = orderRepository.findById(event.referenceId).orElse(null) ?: run {
            logger.warn("Ignoring payment event {} for unknown order {}", event.eventId, event.referenceId)
            return
        }
        if (order.customerId != event.actorId || order.totalAmount.compareTo(event.amount) != 0) {
            throw IllegalStateException("Payment event does not match the authoritative order")
        }

        when (event.eventType) {
            "PaymentCaptured" -> {
                if (order.paymentStatus == PaymentStatus.SUCCESS && order.paymentId == event.transactionId) return
                if (order.status in setOf(OrderStatus.CANCELLED, OrderStatus.REJECTED)) {
                    order.paymentId = event.transactionId
                    order.paymentStatus = PaymentStatus.REFUND_PENDING
                    orderRepository.saveAndFlush(order)
                    paymentModule.refundOrder(requireNotNull(order.orderId))
                    return
                }
                val confirmed = orderService.confirmOrder(requireNotNull(order.orderId), event.transactionId)
                confirmed.loyaltyRewardId?.let { rewardId ->
                    paymentModule.redeemLoyaltyReward(rewardId, confirmed.customerId, requireNotNull(confirmed.orderId))
                }
            }
            "PaymentFailed", "PaymentExpired" -> {
                if (order.paymentStatus in setOf(PaymentStatus.SUCCESS, PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED)) return
                order.paymentId = event.transactionId
                order.paymentStatus = PaymentStatus.FAILED
                orderRepository.saveAndFlush(order)
                if (order.status == OrderStatus.PLACED) {
                    orderService.updateOrderStatus(
                        orderId = requireNotNull(order.orderId),
                        newStatus = OrderStatus.CANCELLED,
                        changedBy = order.customerId,
                        actorRole = OrderActor.CUSTOMER,
                        note = event.reason ?: if (event.eventType == "PaymentExpired") {
                            "Online payment expired"
                        } else {
                            "Online payment failed"
                        },
                    )
                }
            }
            "PaymentRefundPending" -> {
                order.paymentId = event.transactionId
                order.paymentStatus = PaymentStatus.REFUND_PENDING
                orderRepository.saveAndFlush(order)
            }
            "PaymentRefunded" -> {
                order.paymentId = event.transactionId
                order.paymentStatus = PaymentStatus.REFUNDED
                orderRepository.saveAndFlush(order)
            }
        }
    }
}
