package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.service.PaymentAdminPage
import com.pawsnearme.paymentservice.service.PaymentAdminQueryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments/admin/transactions")
class PaymentAdminController(
    private val service: PaymentAdminQueryService
) {
    @GetMapping
    fun search(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(required = false) referenceId: UUID?,
        @RequestParam(required = false) gatewayTransactionId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) fromTime: Instant?,
        @RequestParam(required = false) toTime: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<PaymentAdminPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(
            service.search(referenceId, gatewayTransactionId, status, fromTime, toTime, page, size)
        )
    }

    private fun requireAdmin(userId: String?, role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required")
        }
        if (runCatching { UUID.fromString(userId) }.getOrNull() == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required")
        }
    }
}
