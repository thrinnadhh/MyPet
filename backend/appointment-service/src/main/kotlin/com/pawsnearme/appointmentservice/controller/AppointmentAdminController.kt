package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.service.AdminAppointmentDetail
import com.pawsnearme.appointmentservice.service.AdminAppointmentPage
import com.pawsnearme.appointmentservice.service.AppointmentAdminQueryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/appointments/admin")
class AppointmentAdminController(
    private val service: AppointmentAdminQueryService
) {
    @GetMapping
    fun list(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<AdminAppointmentPage> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(service.list(page, size))
    }

    @GetMapping("/{appointmentId}")
    fun detail(
        @PathVariable appointmentId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AdminAppointmentDetail> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(service.detail(appointmentId))
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
