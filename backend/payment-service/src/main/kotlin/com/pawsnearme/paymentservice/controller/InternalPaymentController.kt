package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.UUID

/**
 * Internal payment mutations are not Admin endpoints. They are invoked by domain
 * services (currently order-service) after those services validate cancellation or
 * dispute business rules and persist the human actor/reason in their own audit log.
 */
@RestController
@RequestMapping("/api/v1/internal/payments")
class InternalPaymentController(
    private val cashfreeGatewayService: CashfreeGatewayService,
    @Value("\${internal.api.secret}") private val internalSecret: String,
) {
    @PostMapping("/refund")
    fun refundOrder(
        @RequestParam orderId: UUID,
        @RequestHeader("X-Internal-Secret", required = false) providedSecret: String?,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
    ): ResponseEntity<Any> {
        requireTrustedOrderService(providedSecret, serviceName)
        return ResponseEntity.ok(cashfreeGatewayService.refundOrder(orderId))
    }

    private fun requireTrustedOrderService(providedSecret: String?, serviceName: String?) {
        val validSecret = internalSecret.isNotBlank() &&
            !providedSecret.isNullOrBlank() &&
            MessageDigest.isEqual(providedSecret.toByteArray(), internalSecret.toByteArray())
        if (!validSecret || serviceName != "order-service") {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Trusted order-service identity required")
        }
    }
}
