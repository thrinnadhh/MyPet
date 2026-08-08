package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.CreateOrderRequest
import com.pawsnearme.orderservice.service.DeliveryContactLookup
import com.pawsnearme.orderservice.service.MerchantOrderQueryService
import com.pawsnearme.orderservice.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val deliveryContactLookup: DeliveryContactLookup,
    private val merchantOrderQueryService: MerchantOrderQueryService,
) {

    @PostMapping
    @Transactional
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Phone", required = false) verifiedAuthPhone: String?,
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }

        val customerId = runCatching { UUID.fromString(authenticatedUserId) }.getOrNull()
            ?: return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Invalid authenticated user context."))

        val ownedContact = deliveryContactLookup.forCustomerAddress(customerId, request.deliveryAddressId)
        val normalizedDeliveryPhone = normalizeIndiaMobile(ownedContact?.phoneNumber)
            ?: return ResponseEntity.badRequest().body(
                mapOf(
                    "code" to "DELIVERY_CONTACT_REQUIRED",
                    "error" to "Add a valid delivery contact number to this address before placing your order."
                )
            )

        val finalRequest = request.copy(customerId = customerId)
        val order = orderService.createOrder(finalRequest)
        order.deliveryContactPhone = normalizedDeliveryPhone
        order.deliveryContactVerified = normalizeIndiaMobile(verifiedAuthPhone) == normalizedDeliveryPhone
        val saved = orderRepository.save(order)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @GetMapping("/{id}")
    fun getOrder(
        @PathVariable id: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val order = orderService.getOrderWithAuth(id, callerId, authenticatedUserRole)
        return ResponseEntity.ok(order)
    }

    @GetMapping("/customer/{customerId}")
    fun getOrdersByCustomer(
        @PathVariable customerId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val orders = orderService.getOrdersByCustomerWithAuth(customerId, callerId, authenticatedUserRole)
        return ResponseEntity.ok(orders)
    }

    @GetMapping("/customer/{customerId}/tracking")
    fun getCustomerOrderTracking(
        @PathVariable customerId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val summaries = orderService.getCustomerOrderSummariesWithAuth(customerId, callerId, authenticatedUserRole)
        return ResponseEntity.ok(summaries)
    }

    @GetMapping("/provider/{providerId}")
    fun getOrdersByProvider(
        @PathVariable providerId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = runCatching { UUID.fromString(authenticatedUserId) }.getOrNull()
            ?: return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Invalid authenticated user context."))
        return ResponseEntity.ok(
            merchantOrderQueryService.listProviderOrders(providerId, callerId, authenticatedUserRole)
        )
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(
        @PathVariable id: UUID,
        @RequestParam(required = false) reason: String?,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val cancelled = orderService.cancelOrder(id, callerId, authenticatedUserRole, reason)
        return ResponseEntity.ok(cancelled)
    }

    @PostMapping("/{id}/reorder")
    fun reorder(
        @PathVariable id: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val result = orderService.revalidateReorder(id, callerId, authenticatedUserRole)
        return ResponseEntity.ok(result)
    }

    @PutMapping("/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: UUID,
        @RequestParam status: OrderStatus,
        @RequestParam(required = false) note: String?,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }
        val updated = orderService.updateOrderStatusWithAuth(
            id,
            status,
            UUID.fromString(authenticatedUserId),
            authenticatedUserRole,
            note
        )
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/{id}/confirm")
    fun confirmOrder(
        @PathVariable id: UUID,
        @RequestParam(required = false) paymentId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }
        val order = orderService.confirmOrderWithAuth(
            id,
            paymentId,
            UUID.fromString(authenticatedUserId),
            authenticatedUserRole
        )
        return ResponseEntity.ok(order)
    }

    private fun normalizeIndiaMobile(value: String?): String? {
        val digits = value?.filter(Char::isDigit) ?: return null
        val local = when {
            digits.length == 10 -> digits
            digits.length == 12 && digits.startsWith("91") -> digits.takeLast(10)
            else -> return null
        }
        if (local.firstOrNull() !in '6'..'9') return null
        return "+91$local"
    }
}
