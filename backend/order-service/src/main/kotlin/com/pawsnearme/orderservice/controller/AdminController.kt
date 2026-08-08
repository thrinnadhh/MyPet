package com.pawsnearme.orderservice.controller

import com.fasterxml.jackson.annotation.JsonAlias
import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Invoice
import com.pawsnearme.orderservice.model.SupportCase
import com.pawsnearme.orderservice.service.AdminControlPlaneService
import com.pawsnearme.orderservice.service.AdminDisputePage
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
    @field:NotBlank @field:Size(min = 3, max = 4000) val resolutionNotes: String
)

data class UpdateDisputeRefundModeRequest(
    @field:JsonAlias("dispute_refund_mode")
    @field:NotBlank
    val disputeRefundMode: String,
    @field:Size(min = 3, max = 500)
    val reason: String = "Admin console refund policy change"
)

@RestController
@RequestMapping("/api/v1/orders")
class AdminController(
    private val orderService: OrderService,
    private val adminControlPlaneService: AdminControlPlaneService
) {

    @GetMapping("/admin/config")
    fun getDisputeRefundMode(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Map<String, String>> {
        requireAdmin(role)
        return ResponseEntity.ok(
            mapOf("dispute_refund_mode" to adminControlPlaneService.getDisputeRefundMode())
        )
    }

    @PostMapping("/admin/config")
    fun updateDisputeRefundMode(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
        @Valid @RequestBody request: UpdateDisputeRefundModeRequest
    ): ResponseEntity<Map<String, String>> {
        requireAdmin(role)
        val updated = adminControlPlaneService.updateDisputeRefundMode(
            requestedMode = request.disputeRefundMode,
            actorId = requireUser(userId),
            reason = request.reason,
            traceId = requestId.orEmpty()
        )
        return ResponseEntity.ok(mapOf("dispute_refund_mode" to updated))
    }

    /** Compatibility response remains an array, but is now capped at the newest 100 disputes. */
    @GetMapping("/disputes")
    fun listDisputes(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<Dispute>> {
        requireAdmin(role)
        return ResponseEntity.ok(adminControlPlaneService.listDisputes(page = 0, size = 100).content)
    }

    @GetMapping("/admin/disputes")
    fun listAdminDisputes(
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ResponseEntity<AdminDisputePage> {
        requireAdmin(role)
        return ResponseEntity.ok(adminControlPlaneService.listDisputes(page, size))
    }

    @PostMapping("/disputes")
    fun createDispute(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @Valid @RequestBody request: CreateDisputeRequest
    ): ResponseEntity<Dispute> {
        val actorId = requireUser(userId)
        return ResponseEntity.ok(
            orderService.createDisputeWithAuth(request.orderId, request.reason, actorId, role)
        )
    }

    @PostMapping("/disputes/{id}/resolve")
    fun resolveDispute(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
        @Valid @RequestBody request: ResolveDisputeRequest
    ): ResponseEntity<Dispute> {
        requireAdmin(role)
        return ResponseEntity.ok(
            adminControlPlaneService.resolveDispute(
                disputeId = id,
                requestedDecision = request.decision,
                resolutionNotes = request.resolutionNotes,
                actorId = requireUser(userId),
                traceId = requestId.orEmpty()
            )
        )
    }

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
        return ResponseEntity.ok(
            orderService.createSupportCase(
                title = request.title,
                detail = request.detail,
                actionType = request.actionType,
                entityType = request.entityType,
                entityId = request.entityId,
                createdByUserId = actorId
            )
        )
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
        return ResponseEntity.ok(orderService.resolveSupportCase(id, request.resolutionNotes, actorId))
    }

    @GetMapping("/{orderId}/invoice")
    fun getInvoice(
        @PathVariable orderId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Invoice> = ResponseEntity.ok(
        orderService.getInvoiceByOrderIdWithAuth(orderId, requireUser(userId), role)
    )

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
