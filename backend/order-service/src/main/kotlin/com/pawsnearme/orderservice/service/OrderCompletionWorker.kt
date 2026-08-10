package com.pawsnearme.orderservice.service

import com.pawsnearme.common.scheduling.WorkerScheduler
import com.pawsnearme.orderservice.model.OrderActor
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
@WorkerScheduler
class OrderCompletionWorker(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    @Value("\${order.auto-complete.hours-after-delivery:1}")
    private val hoursAfterDelivery: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val systemActorId = UUID.fromString("00000000-0000-4000-8000-000000000001")

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "order_completeDeliveredOrders", lockAtMostFor = "PT2M", lockAtLeastFor = "PT55S")
    fun completeDeliveredOrders() {
        val cutoff = Instant.now().minusSeconds(hoursAfterDelivery * 3600)
        val delivered = orderRepository.findByStatusAndDeliveredAtBefore(OrderStatus.DELIVERED, cutoff)
        if (delivered.isEmpty()) return

        delivered.forEach { order ->
            try {
                orderService.updateOrderStatus(
                    orderId = order.orderId!!,
                    newStatus = OrderStatus.COMPLETED,
                    changedBy = systemActorId,
                    actorRole = OrderActor.SYSTEM,
                    note = "Auto-completed after delivery window",
                )
                log.info("Auto-completed order {}", order.orderId)
            } catch (e: Exception) {
                log.warn("Could not auto-complete order {}: {}", order.orderId, e.message)
            }
        }
    }
}
