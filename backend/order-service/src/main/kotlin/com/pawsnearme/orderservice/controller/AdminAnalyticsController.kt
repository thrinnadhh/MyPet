package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.AdminAnalyticsService
import com.pawsnearme.orderservice.service.AdminBusinessAnalytics
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/admin/analytics")
class AdminAnalyticsController(
    private val adminAnalyticsService: AdminAnalyticsService,
) {
    @GetMapping
    fun analytics(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
    ): ResponseEntity<AdminBusinessAnalytics> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(adminAnalyticsService.snapshot(from, to))
    }

    private fun requireAdmin(userId: String?, role: String?): UUID {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw OrderAccessDeniedException("Administrator role required.")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw OrderAccessDeniedException("Valid administrator identity required.") }
    }
}
