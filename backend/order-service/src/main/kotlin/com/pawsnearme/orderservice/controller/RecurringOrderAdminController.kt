package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.AdminRecurringSubscriptionPage
import com.pawsnearme.orderservice.service.AdminRecurringTraceView
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import com.pawsnearme.orderservice.service.RecurringOrderAdminService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/admin/subscriptions")
class RecurringOrderAdminController(
    private val service: RecurringOrderAdminService
) {
    @GetMapping
    fun list(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<AdminRecurringSubscriptionPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(service.list(page, size))
    }

    @GetMapping("/{subscriptionId}")
    fun trace(
        @PathVariable subscriptionId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<AdminRecurringTraceView> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(service.trace(subscriptionId, page, size))
    }

    private fun requireAdmin(userId: String?, role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw OrderAccessDeniedException("Administrator role required")
        }
        if (runCatching { UUID.fromString(userId) }.getOrNull() == null) {
            throw OrderAccessDeniedException("Valid authenticated administrator identity is required")
        }
    }
}