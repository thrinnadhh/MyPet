package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.CreateOrderRequest
import com.pawsnearme.orderservice.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository
) {

    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        val finalRequest = if (authenticatedUserId != null) {
            request.copy(customerId = UUID.fromString(authenticatedUserId))
        } else {
            request
        }
        val order = orderService.createOrder(finalRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(order)
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: UUID): ResponseEntity<Order> {
        val order = orderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Order with ID $id not found") }
        return ResponseEntity.ok(order)
    }

    @GetMapping("/customer/{customerId}")
    fun getOrdersByCustomer(@PathVariable customerId: UUID): ResponseEntity<List<Order>> {
        val orders = orderRepository.findByCustomerId(customerId)
        return ResponseEntity.ok(orders)
    }

    @GetMapping("/provider/{providerId}")
    fun getOrdersByProvider(@PathVariable providerId: UUID): ResponseEntity<List<Order>> {
        val orders = orderRepository.findByProviderId(providerId)
        return ResponseEntity.ok(orders)
    }

    @PutMapping("/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: UUID,
        @RequestParam status: OrderStatus,
        @RequestParam(required = false) note: String?,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        val changerId = if (authenticatedUserId != null) {
            UUID.fromString(authenticatedUserId)
        } else {
            UUID.randomUUID() // fallback if no auth header
        }
        val updated = orderService.updateOrderStatus(id, status, changerId, note)
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/{id}/confirm")
    fun confirmOrder(
        @PathVariable id: UUID,
        @RequestParam(required = false) paymentId: UUID?
    ): ResponseEntity<Any> {
        val order = orderService.confirmOrder(id, paymentId)
        return ResponseEntity.ok(order)
    }
}
