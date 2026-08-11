package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class DailyRevenueEntry(
    val date: String,
    val gmv: BigDecimal,
    val orderCount: Int
)

data class AnalyticsSummaryResponse(
    val totalGmv: BigDecimal,
    val totalOrders: Int,
    val completedOrders: Int,
    val averageOrderValue: BigDecimal,
    val dailyRevenue: List<DailyRevenueEntry>
)

@RestController
@RequestMapping("/api/v1/orders/admin/analytics")
class AdminAnalyticsController(private val orderRepository: OrderRepository) {

    @GetMapping("/summary")
    fun getAnalyticsSummary(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AnalyticsSummaryResponse> {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw OrderAccessDeniedException("Administrator role required to access analytics summary.")
        }

        val allOrders = orderRepository.findAll()
        val validOrders = allOrders.filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.REJECTED }
        
        val totalGmv = validOrders.fold(BigDecimal.ZERO) { acc, o -> acc.add(o.totalAmount) }
        val totalOrders = validOrders.size
        val completedOrders = validOrders.count { it.status == OrderStatus.COMPLETED || it.status == OrderStatus.DELIVERED }
        val avgOrderValue = if (totalOrders > 0) {
            totalGmv.divide(BigDecimal(totalOrders), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
        val dailyMap = validOrders.groupBy { formatter.format(it.placedAt) }
        val dailyEntries = dailyMap.map { (dateStr, orders) ->
            val dayGmv = orders.fold(BigDecimal.ZERO) { acc, o -> acc.add(o.totalAmount) }
            DailyRevenueEntry(date = dateStr, gmv = dayGmv, orderCount = orders.size)
        }.sortedByDescending { it.date }

        return ResponseEntity.ok(
            AnalyticsSummaryResponse(
                totalGmv = totalGmv,
                totalOrders = totalOrders,
                completedOrders = completedOrders,
                averageOrderValue = avgOrderValue,
                dailyRevenue = dailyEntries
            )
        )
    }
}
