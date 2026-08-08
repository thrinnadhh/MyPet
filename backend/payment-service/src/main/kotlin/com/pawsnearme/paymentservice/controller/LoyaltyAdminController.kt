package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.CustomerLoyaltyAccount
import com.pawsnearme.paymentservice.model.LoyaltyAuditLog
import com.pawsnearme.paymentservice.model.LoyaltyLedgerEntry
import com.pawsnearme.paymentservice.service.LoyaltyAdminPage
import com.pawsnearme.paymentservice.service.LoyaltyAdminQueryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments/loyalty/admin")
class LoyaltyAdminController(
    private val queryService: LoyaltyAdminQueryService
) {
    @GetMapping("/accounts")
    fun accounts(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<LoyaltyAdminPage<CustomerLoyaltyAccount>> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(queryService.accounts(page, size))
    }

    @GetMapping("/ledger")
    fun ledger(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam customerId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<LoyaltyAdminPage<LoyaltyLedgerEntry>> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(queryService.customerLedger(customerId, page, size))
    }

    @GetMapping("/audit")
    fun audit(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<LoyaltyAdminPage<LoyaltyAuditLog>> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(queryService.audit(page, size))
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
