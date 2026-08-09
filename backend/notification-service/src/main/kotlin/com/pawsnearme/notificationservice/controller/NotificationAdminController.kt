package com.pawsnearme.notificationservice.controller

import com.pawsnearme.notificationservice.service.AdminEmailDeliveryPage
import com.pawsnearme.notificationservice.service.AdminEmailDeliveryView
import com.pawsnearme.notificationservice.service.AdminNotificationAuditPage
import com.pawsnearme.notificationservice.service.NotificationAdminService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class NotificationRetryRequest(
    @field:Size(min = 3, max = 500)
    val reason: String,
)

@RestController
@RequestMapping("/api/v1/notifications/admin")
class NotificationAdminController(
    private val notificationAdminService: NotificationAdminService,
) {
    @GetMapping("/email-deliveries")
    fun listEmailDeliveries(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<AdminEmailDeliveryPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(notificationAdminService.list(page, size, status))
    }

    @PostMapping("/email-deliveries/{deliveryId}/retry")
    fun retryFailedEmail(
        @PathVariable deliveryId: UUID,
        @Valid @RequestBody request: NotificationRetryRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
    ): ResponseEntity<AdminEmailDeliveryView> {
        val actorId = requireAdmin(userId, role)
        return ResponseEntity.ok(notificationAdminService.retryFailed(deliveryId, actorId, request.reason, requestId))
    }

    @GetMapping("/audit")
    fun audit(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<AdminNotificationAuditPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(notificationAdminService.audit(page, size))
    }

    private fun requireAdmin(userId: String?, role: String?): UUID {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required") }
    }
}
