package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.service.CatalogService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/catalog")
class InternalCatalogController(
    private val catalogService: CatalogService,
    @Value("\${internal.api.secret:dev-internal-secret}")
    private val internalSecret: String = "dev-internal-secret",
    @Value("\${gateway.trust.secret:dev-gateway-secret-key}")
    private val gatewayTrustSecret: String = "dev-gateway-secret-key"
) {

    private fun verifyInternalSecret(secret: String?) {
        if (secret.isNullOrBlank() || (secret != internalSecret && secret != gatewayTrustSecret)) {
            throw CatalogAccessDeniedException("Forbidden: invalid or missing internal secret")
        }
    }

    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStockInternal(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader(value = "X-Internal-Secret", required = false) xInternalSecret: String?,
        @RequestHeader(value = "X-Internal-Gateway-Secret", required = false) xGatewaySecret: String?
    ): ResponseEntity<Offering> {
        verifyInternalSecret(xInternalSecret ?: xGatewaySecret)
        val updated = catalogService.decrementStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
    }

    @PutMapping("/offerings/{offeringId}/restore-stock")
    fun restoreStockInternal(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader(value = "X-Internal-Secret", required = false) xInternalSecret: String?,
        @RequestHeader(value = "X-Internal-Gateway-Secret", required = false) xGatewaySecret: String?
    ): ResponseEntity<Offering> {
        verifyInternalSecret(xInternalSecret ?: xGatewaySecret)
        val updated = catalogService.restoreStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
    }

    @ExceptionHandler(CatalogAccessDeniedException::class)
    fun handleAccessDenied(ex: CatalogAccessDeniedException): ResponseEntity<Any> {
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
            .body(mapOf("error" to ex.message))
    }
}
