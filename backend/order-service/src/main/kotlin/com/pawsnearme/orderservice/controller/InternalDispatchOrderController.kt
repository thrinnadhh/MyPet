package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.OrderService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Service-to-service order lifecycle boundary owned by dispatch-service.
 *
 * Gateway trust proves that the request came through an approved internal
 * transport. The separate internal API secret proves that the caller is an
 * authorized backend service rather than a public client reusing identity
 * headers. Only delivery transitions owned by dispatch are accepted here.
 */
@RestController
@RequestMapping("/internal/api/v1/orders")
class InternalDispatchOrderController(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    @Value("\${internal.api.secret:}") private val internalApiSecret: String
) {

    @PutMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: UUID,
        @RequestParam status: OrderStatus,
        @RequestParam(required = false) note: String?,
        @RequestHeader(CAPTAIN_ID_HEADER, required = false) captainIdHeader: String?,
        @RequestHeader(INTERNAL_API_SECRET_HEADER, required = false) providedSecret: String?
    ): ResponseEntity<Any> {
        if (!hasValidInternalSecret(providedSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Dispatch service authorization failed."))
        }

        val captainId = captainIdHeader
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return ResponseEntity.badRequest()
                .body(mapOf("error" to "A valid X-Captain-Id header is required."))

        val order = orderRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Order with ID $id not found"))

        val transitionAllowed = when (status) {
            OrderStatus.ASSIGNED ->
                order.status == OrderStatus.READY_FOR_PICKUP &&
                    (order.captainId == null || order.captainId == captainId)

            OrderStatus.PICKED_UP ->
                order.status == OrderStatus.ASSIGNED && order.captainId == captainId

            OrderStatus.DELIVERED ->
                order.status == OrderStatus.PICKED_UP && order.captainId == captainId

            else -> false
        }

        if (!transitionAllowed) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "Dispatch cannot transition order from ${order.status} to $status for captain $captainId."
                )
            )
        }

        return ResponseEntity.ok(orderService.updateOrderStatus(id, status, captainId, note))
    }

    private fun hasValidInternalSecret(providedSecret: String?): Boolean {
        if (internalApiSecret.isBlank() || providedSecret.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            internalApiSecret.toByteArray(StandardCharsets.UTF_8),
            providedSecret.toByteArray(StandardCharsets.UTF_8)
        )
    }

    companion object {
        const val INTERNAL_API_SECRET_HEADER = "X-Internal-Api-Secret"
        const val CAPTAIN_ID_HEADER = "X-Captain-Id"
    }
}
