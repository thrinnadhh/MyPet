package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

data class AdminBusinessAnalytics(
    val fromTime: Instant,
    val toTime: Instant,
    val ordersPlaced: Long,
    val completedOrders: Long,
    val cancelledOrRejectedOrders: Long,
    val failedPayments: Long,
    val distinctCustomers: Long,
    val distinctProviders: Long,
    val grossMerchandiseValue: BigDecimal,
    val averageCompletedOrderValue: BigDecimal,
    val completionRatePct: BigDecimal,
    val cancellationRatePct: BigDecimal,
    val generatedAt: Instant,
)

@Service
class AdminAnalyticsService(
    private val orderRepository: OrderRepository,
) {
    @Transactional(readOnly = true)
    fun snapshot(fromTime: Instant, toTime: Instant, now: Instant = Instant.now()): AdminBusinessAnalytics {
        require(fromTime.isBefore(toTime)) { "Analytics start time must be before end time" }
        require(!toTime.isAfter(now.plusSeconds(300))) { "Analytics end time cannot be in the future" }
        require(Duration.between(fromTime, toTime) <= MAX_RANGE) { "Analytics range cannot exceed 366 days" }

        val placed = orderRepository.countPlacedInRange(fromTime, toTime)
        val completed = orderRepository.countCompletedInRange(fromTime, toTime)
        val cancelled = orderRepository.countCancelledInRange(fromTime, toTime)
        val gmv = orderRepository.sumCompletedGmvInRange(fromTime, toTime).setScale(2, RoundingMode.HALF_UP)
        val averageOrderValue = if (completed == 0L) BigDecimal.ZERO.setScale(2) else
            gmv.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
        val completionRate = percentage(completed, placed)
        val cancellationRate = percentage(cancelled, placed)

        return AdminBusinessAnalytics(
            fromTime = fromTime,
            toTime = toTime,
            ordersPlaced = placed,
            completedOrders = completed,
            cancelledOrRejectedOrders = cancelled,
            failedPayments = orderRepository.countFailedPaymentsInRange(fromTime, toTime),
            distinctCustomers = orderRepository.countDistinctCustomersInRange(fromTime, toTime),
            distinctProviders = orderRepository.countDistinctProvidersInRange(fromTime, toTime),
            grossMerchandiseValue = gmv,
            averageCompletedOrderValue = averageOrderValue,
            completionRatePct = completionRate,
            cancellationRatePct = cancellationRate,
            generatedAt = now,
        )
    }

    private fun percentage(numerator: Long, denominator: Long): BigDecimal =
        if (denominator == 0L) BigDecimal.ZERO.setScale(2)
        else BigDecimal.valueOf(numerator)
            .multiply(BigDecimal("100"))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)

    companion object {
        private val MAX_RANGE: Duration = Duration.ofDays(366)
    }
}
