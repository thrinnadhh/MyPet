package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.CreateRecurringOrderRequest
import com.pawsnearme.orderservice.service.RecurringOrderConfirmation
import com.pawsnearme.orderservice.service.RecurringOrderService
import com.pawsnearme.orderservice.service.RecurringOrderView
import com.pawsnearme.orderservice.service.UpdateRecurringOrderRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/subscriptions")
class RecurringOrderController(
    private val recurringOrderService: RecurringOrderService
) {
    @PostMapping
    fun create(
        @RequestBody request: CreateRecurringOrderRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<RecurringOrderView> = ResponseEntity.status(HttpStatus.CREATED)
        .body(recurringOrderService.create(customerId(userId), request))

    @GetMapping
    fun list(
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<List<RecurringOrderView>> =
        ResponseEntity.ok(recurringOrderService.list(customerId(userId)))

    @PatchMapping("/{subscriptionId}")
    fun update(
        @PathVariable subscriptionId: UUID,
        @RequestBody request: UpdateRecurringOrderRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<RecurringOrderView> =
        ResponseEntity.ok(recurringOrderService.update(customerId(userId), subscriptionId, request))

    @PostMapping("/{subscriptionId}/confirm")
    fun confirm(
        @PathVariable subscriptionId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<RecurringOrderConfirmation> =
        ResponseEntity.ok(recurringOrderService.confirm(customerId(userId), subscriptionId))

    private fun customerId(value: String?): UUID = try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid customer identity required.")
    }
}
