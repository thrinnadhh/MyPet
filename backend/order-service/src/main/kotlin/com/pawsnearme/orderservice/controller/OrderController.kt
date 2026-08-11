package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.CheckoutIntegrityService
import com.pawsnearme.orderservice.service.CreateOrderRequest
import com.pawsnearme.orderservice.service.DeliveryContactLookup
import com.pawsnearme.orderservice.service.MerchantOrderQueryService
import com.pawsnearme.orderservice.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val checkoutIntegrityService: CheckoutIntegrityService,
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
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }

        val customerId = runCatching { UUID.fromString(authenticatedUserId) }.getOrNull()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Invalid authenticated user context."))

        val ownedContact = deliveryContactLookup.forCustomerAddress(customerId, request.deliveryAddressId)
        val normalizedDeliveryPhone = normalizeIndiaMobile(ownedContact?.phoneNumber)
            ?: return ResponseEntity.badRequest().body(
                mapOf(
                    "code" to "DELIVERY_CONTACT_REQUIRED",
                    "error" to "Add a valid delivery contact number to this address before placing your order."
                )
            )

        val order = checkoutIntegrityService.createOrder(
            request.copy(customerId = customerId),
            idempotencyKey,
        )
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
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(orderService.getOrderWithAuth(id, UUID.fromString(authenticatedUserId), authenticatedUserRole))
    }

    @GetMapping("/customer/{customerId}")
    fun getOrdersByCustomer(
        @PathVariable customerId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(orderService.getOrdersByCustomerWithAuth(customerId, UUID.fromString(authenticatedUserId), authenticatedUserRole))
    }

    @GetMapping("/customer/{customerId}/tracking")
    fun getCustomerOrderTracking(
        @PathVariable customerId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(orderService.getCustomerOrderSummariesWithAuth(customerId, UUID.fromString(authenticatedUserId), authenticatedUserRole))
    }

    @GetMapping("/provider/{providerId}")
    fun getOrdersByProvider(
        @PathVariable providerId: UUID,
        @RequestParam(required = false) page: Int?,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = runCatching { UUID.fromString(authenticatedUserId) }.getOrNull()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Invalid authenticated user context."))
        return if (page == null) {
            ResponseEntity.ok(merchantOrderQueryService.listProviderOrders(providerId, callerId, authenticatedUserRole))
        } else {
            ResponseEntity.ok(
                merchantOrderQueryService.listProviderOrdersPage(
                    providerId = providerId,
                    callerId = callerId,
                    callerRole = authenticatedUserRole,
                    page = page,
                    size = size,
                )
            )
        }
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(
        @PathVariable id: UUID,
        @RequestParam(required = false) reason: String?,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(orderService.cancelOrder(id, UUID.fromString(authenticatedUserId), authenticatedUserRole, reason))
    }

    @PostMapping("/{id}/reorder")
    fun reorder(
        @PathVariable id: UUID,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(orderService.revalidateReorder(id, UUID.fromString(authenticatedUserId), authenticatedUserRole))
    }

    @PutMapping("/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: UUID,
        @RequestParam status: OrderStatus,
        @RequestParam(required = false) note: String?,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(
            orderService.updateOrderStatusWithAuth(id, status, UUID.fromString(authenticatedUserId), authenticatedUserRole, note)
        )
    }

    /** Retained for internal/backward-compatible recovery; customer checkout no longer calls this endpoint. */
    @PostMapping("/{id}/confirm")
    fun confirmOrder(
        @PathVariable id: UUID,
        @RequestParam(required = false) paymentId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader(value = "X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        return ResponseEntity.ok(
            orderService.confirmOrderWithAuth(id, paymentId, UUID.fromString(authenticatedUserId), authenticatedUserRole)
        )
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
