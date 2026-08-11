package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.MerchantOrderOperationalService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class MerchantOrderOperationalController(
    private val operationalService: MerchantOrderOperationalService,
) {
    @GetMapping("/{orderId}/merchant-detail")
    fun detail(
        @PathVariable orderId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedRole: String?,
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("code" to "MERCHANT_CONTEXT_REQUIRED", "message" to "Missing authenticated merchant context."))
        }
        if (!authenticatedRole.equals("MERCHANT", ignoreCase = true)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("code" to "MERCHANT_REQUIRED", "message" to "Merchant access is required."))
        }
        val merchantUserId = runCatching { UUID.fromString(authenticatedUserId) }.getOrNull()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("code" to "MERCHANT_CONTEXT_INVALID", "message" to "Invalid authenticated merchant context."))
        return ResponseEntity.ok(operationalService.detail(orderId, merchantUserId))
    }
}
