package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class AdminAnalyticsServiceTests {
    private val orderRepository: OrderRepository = mock()
    private val service = AdminAnalyticsService(orderRepository)

    @Test
    fun `snapshot uses database aggregates and calculates stable percentages`() {
        val now = Instant.parse("2026-08-09T00:00:00Z")
        val from = now.minus(30, ChronoUnit.DAYS)
        whenever(orderRepository.countPlacedInRange(from, now)).thenReturn(100)
        whenever(orderRepository.countCompletedInRange(from, now)).thenReturn(80)
        whenever(orderRepository.countCancelledInRange(from, now)).thenReturn(15)
        whenever(orderRepository.sumCompletedGmvInRange(from, now)).thenReturn(BigDecimal("40000.00"))
        whenever(orderRepository.countFailedPaymentsInRange(from, now)).thenReturn(5)
        whenever(orderRepository.countDistinctCustomersInRange(from, now)).thenReturn(62)
        whenever(orderRepository.countDistinctProvidersInRange(from, now)).thenReturn(18)

        val result = service.snapshot(from, now, now)

        assertEquals(100, result.ordersPlaced)
        assertEquals(80, result.completedOrders)
        assertEquals(BigDecimal("40000.00"), result.grossMerchandiseValue)
        assertEquals(BigDecimal("500.00"), result.averageCompletedOrderValue)
        assertEquals(BigDecimal("80.00"), result.completionRatePct)
        assertEquals(BigDecimal("15.00"), result.cancellationRatePct)
        assertEquals(62, result.distinctCustomers)
        assertEquals(18, result.distinctProviders)
        assertEquals(5, result.failedPayments)
    }

    @Test
    fun `zero order range avoids division by zero`() {
        val now = Instant.parse("2026-08-09T00:00:00Z")
        val from = now.minus(1, ChronoUnit.DAYS)
        whenever(orderRepository.countPlacedInRange(from, now)).thenReturn(0)
        whenever(orderRepository.countCompletedInRange(from, now)).thenReturn(0)
        whenever(orderRepository.countCancelledInRange(from, now)).thenReturn(0)
        whenever(orderRepository.sumCompletedGmvInRange(from, now)).thenReturn(BigDecimal.ZERO)
        whenever(orderRepository.countFailedPaymentsInRange(from, now)).thenReturn(0)
        whenever(orderRepository.countDistinctCustomersInRange(from, now)).thenReturn(0)
        whenever(orderRepository.countDistinctProvidersInRange(from, now)).thenReturn(0)

        val result = service.snapshot(from, now, now)

        assertEquals(BigDecimal("0.00"), result.averageCompletedOrderValue)
        assertEquals(BigDecimal("0.00"), result.completionRatePct)
        assertEquals(BigDecimal("0.00"), result.cancellationRatePct)
    }

    @Test
    fun `snapshot rejects reversed future and unbounded ranges`() {
        val now = Instant.parse("2026-08-09T00:00:00Z")
        assertThrows<IllegalArgumentException> { service.snapshot(now, now.minusSeconds(1), now) }
        assertThrows<IllegalArgumentException> { service.snapshot(now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.MINUTES), now) }
        assertThrows<IllegalArgumentException> { service.snapshot(now.minus(367, ChronoUnit.DAYS), now, now) }
    }
}
