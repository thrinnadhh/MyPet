package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Invoice
import com.pawsnearme.orderservice.model.SupportCase
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import com.pawsnearme.orderservice.service.OrderService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateSupportCaseRequest(
    val title: String,
    val detail: String,
    val actionType: String,
    val entityType: String? = null,
    val entityId: UUID? = null
)

data class ResolveSupportCaseRequest(
    val resolutionNotes: String? = null
)

data class CreateDisputeRequest(
    @field:NotNull val orderId: UUID,
    @field:NotBlank @field:Size(max = 2000) val reason: String
)

data class ResolveDisputeRequest(
    @field:NotBlank val decision: String,
    @field:Size(max = 4000) val resolutionNotes: String? = null
)

@RestController
@RequestMapping("/api/v1/orders")
class AdminController(private val orderService: OrderService) {

    // --- System Config ---

    @GetMapping("/admin/config")
    fun getDisputeRefundMode(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Map<String, String>> {
        requireAdmin(role)
        val mode = orderService.getDisputeRefundMode()
        return ResponseEntity.ok(mapOf("dispute_refund_mode" to mode))
    }

    @PostMapping("/admin/config")
    fun updateDisputeRefundMode(
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        requireAdmin(role)
        val mode = request["dispute_refund_mode"] ?: throw IllegalArgumentException("Missing dispute_refund_mode")
        val updated = orderService.updateDisputeRefundMode(mode)
        return ResponseEntity.ok(mapOf("dispute_refund_mode" to updated))
    }

    // --- Disputes ---

    @GetMapping("/disputes")
    fun listDisputes(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<Dispute>> {
        requireAdmin(role)
        return ResponseEntity.ok(orderService.listDisputes())
    }

    @PostMapping("/disputes")
    fun createDispute(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @Valid @RequestBody request: CreateDisputeRequest
    ): ResponseEntity<Dispute> {
        val actorId = requireUser(userId)
        val dispute = orderService.createDisputeWithAuth(request.orderId, request.reason, actorId, role)
        return ResponseEntity.ok(dispute)
    }

    @PostMapping("/disputes/{id}/resolve")
    fun resolveDispute(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @Valid @RequestBody request: ResolveDisputeRequest
    ): ResponseEntity<Dispute> {
        requireAdmin(role)
        val dispute = orderService.resolveDispute(id, request.decision, request.resolutionNotes)
        return ResponseEntity.ok(dispute)
    }

    // --- Support Cases ---

    @GetMapping("/admin/support-cases")
    fun listSupportCases(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<SupportCase>> {
        requireAdmin(role)
        return ResponseEntity.ok(orderService.listSupportCases())
    }

    @PostMapping("/admin/support-cases")
    fun createSupportCase(
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestBody request: CreateSupportCaseRequest
    ): ResponseEntity<SupportCase> {
        requireAdmin(role)
        val actorId = requireUser(xUserId)
        val supportCase = orderService.createSupportCase(
            title = request.title,
            detail = request.detail,
            actionType = request.actionType,
            entityType = request.entityType,
            entityId = request.entityId,
            createdByUserId = actorId
        )
        return ResponseEntity.ok(supportCase)
    }

    @PostMapping("/admin/support-cases/{id}/resolve")
    fun resolveSupportCase(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestBody request: ResolveSupportCaseRequest
    ): ResponseEntity<SupportCase> {
        requireAdmin(role)
        val actorId = requireUser(xUserId)
        val supportCase = orderService.resolveSupportCase(id, request.resolutionNotes, actorId)
        return ResponseEntity.ok(supportCase)
    }

    // --- Invoices ---

    @GetMapping("/{orderId}/invoice")
    fun getInvoice(
        @PathVariable orderId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Invoice> {
        return ResponseEntity.ok(orderService.getInvoiceByOrderIdWithAuth(orderId, requireUser(userId), role))
    }

    private fun requireAdmin(role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) {
            throw OrderAccessDeniedException("Administrator role required.")
        }
    }

    private fun requireUser(userId: String?): UUID {
        if (userId.isNullOrBlank()) {
            throw OrderAccessDeniedException("Valid authenticated user context is required.")
        }
        return runCatching { UUID.fromString(userId) }
            .getOrElse { throw OrderAccessDeniedException("Valid authenticated user context is required.") }
    }
}
