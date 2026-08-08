package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.service.AdminOrderDetail
import com.pawsnearme.orderservice.service.AdminOrderPage
import com.pawsnearme.orderservice.service.AdminOrderQueryService
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/admin/orders")
class AdminOrderQueryController(
    private val service: AdminOrderQueryService
) {
    @GetMapping
    fun search(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(required = false) orderId: UUID?,
        @RequestParam(required = false) customerId: UUID?,
        @RequestParam(required = false) providerId: UUID?,
        @RequestParam(required = false) paymentId: UUID?,
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) fromTime: Instant?,
        @RequestParam(required = false) toTime: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<AdminOrderPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(
            service.search(orderId, customerId, providerId, paymentId, status, fromTime, toTime, page, size)
        )
    }

    @GetMapping("/{orderId}")
    fun detail(
        @PathVariable orderId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AdminOrderDetail> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(service.detail(orderId))
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