package com.pawsnearme.notificationservice.controller

import com.pawsnearme.notificationservice.service.AdminEmailDeliveryPage
import com.pawsnearme.notificationservice.service.AdminEmailDeliveryView
import com.pawsnearme.notificationservice.service.NotificationAdminService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications/admin")
class NotificationAdminController(
    private val notificationAdminService: NotificationAdminService,
) {
    @GetMapping("/email-deliveries")
    fun listEmailDeliveries(
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<AdminEmailDeliveryPage> {
        requireAdmin(role)
        return ResponseEntity.ok(notificationAdminService.list(page, size, status))
    }

    @PostMapping("/email-deliveries/{deliveryId}/retry")
    fun retryFailedEmail(
        @PathVariable deliveryId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
    ): ResponseEntity<AdminEmailDeliveryView> {
        requireAdmin(role)
        return ResponseEntity.ok(notificationAdminService.retryFailed(deliveryId))
    }

    private fun requireAdmin(role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required")
        }
    }
}
