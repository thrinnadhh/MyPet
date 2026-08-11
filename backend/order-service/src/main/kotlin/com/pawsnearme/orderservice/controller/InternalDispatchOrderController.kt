package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.OrderActor
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.OrderService
import com.pawsnearme.orderservice.service.OrderTransitionConflictException
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
 * headers. Delivery state changes are still validated by the canonical order
 * transition policy in OrderService.
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

        val actor = when (status) {
            OrderStatus.ASSIGNED -> {
                if (order.captainId != null && order.captainId != captainId) {
                    throw OrderTransitionConflictException("Order is already assigned to another captain.")
                }
                OrderActor.DISPATCH
            }
            OrderStatus.PICKED_UP, OrderStatus.DELIVERED -> {
                if (order.captainId != captainId) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(mapOf("error" to "Authenticated captain is not assigned to this order."))
                }
                OrderActor.CAPTAIN
            }
            else -> throw OrderTransitionConflictException("Dispatch cannot request order status $status.")
        }

        return ResponseEntity.ok(
            orderService.updateOrderStatus(
                orderId = id,
                newStatus = status,
                changedBy = captainId,
                actorRole = actor,
                note = note
            )
        )
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
