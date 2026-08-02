package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.AdminAuditView
import com.pawsnearme.orderservice.service.AdminOperationsService
import com.pawsnearme.orderservice.service.AdminOperationsSnapshot
import com.pawsnearme.orderservice.service.ServiceAreaUpdateRequest
import com.pawsnearme.orderservice.service.ServiceAreaView
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/admin/operations")
class AdminOperationsController(
    private val adminOperationsService: AdminOperationsService
) {
    @GetMapping("/snapshot")
    fun snapshot(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<AdminOperationsSnapshot> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(adminOperationsService.snapshot())
    }

    @GetMapping("/service-areas")
    fun serviceAreas(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<ServiceAreaView>> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(adminOperationsService.listServiceAreas())
    }

    @PutMapping("/service-areas/{pincode}")
    fun updateServiceArea(
        @PathVariable pincode: String,
        @RequestBody request: ServiceAreaUpdateRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
        @RequestHeader("X-Trace-Id", required = false) traceId: String?
    ): ResponseEntity<ServiceAreaView> {
        val actorId = requireAdmin(userId, role)
        val updated = adminOperationsService.updateServiceArea(
            pincode = pincode,
            request = request,
            actorId = actorId,
            traceId = requestId ?: traceId ?: UUID.randomUUID().toString()
        )
        return ResponseEntity.ok(updated)
    }

    @GetMapping("/audit-logs")
    fun auditLogs(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<AdminAuditView>> {
        requireAdmin(userId, role)
        return ResponseEntity.ok(adminOperationsService.listAuditLogs(limit))
    }

    private fun requireAdmin(userId: String?, role: String?): UUID {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required.")
        }
        return try {
            UUID.fromString(userId)
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid administrator identity required.")
        }
    }
}
