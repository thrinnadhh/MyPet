package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.service.CatalogService
import com.pawsnearme.catalogservice.service.InternalStockMutationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/catalog")
class InternalCatalogController(
    private val mutationService: InternalStockMutationService,
    private val catalogService: CatalogService,
    @Value("\${internal.api.secret}") private val internalSecret: String
) {
    @GetMapping("/offerings/{offeringId}")
    fun getOfferingInternal(
        @PathVariable offeringId: UUID,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
        @RequestHeader("X-Internal-Secret", required = false) suppliedSecret: String?
    ): ResponseEntity<Offering> {
        if (!isAuthorizedOrderService(serviceName, suppliedSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(catalogService.getOfferingById(offeringId))
    }

    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStockInternal(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
        @RequestHeader("X-Internal-Secret", required = false) suppliedSecret: String?,
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: UUID?
    ): ResponseEntity<Offering> = mutate(
        offeringId, quantity, serviceName, suppliedSecret, idempotencyKey, "DECREMENT"
    )

    @PutMapping("/offerings/{offeringId}/restore-stock")
    fun restoreStockInternal(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
        @RequestHeader("X-Internal-Secret", required = false) suppliedSecret: String?,
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: UUID?
    ): ResponseEntity<Offering> = mutate(
        offeringId, quantity, serviceName, suppliedSecret, idempotencyKey, "RESTORE"
    )

    private fun mutate(
        offeringId: UUID,
        quantity: Int,
        serviceName: String?,
        suppliedSecret: String?,
        idempotencyKey: UUID?,
        operation: String
    ): ResponseEntity<Offering> {
        if (!isAuthorizedOrderService(serviceName, suppliedSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val key = idempotencyKey ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(mutationService.mutate(key, offeringId, quantity, operation))
    }

    private fun isAuthorizedOrderService(serviceName: String?, suppliedSecret: String?): Boolean =
        serviceName == "order-service" && suppliedSecret != null &&
            MessageDigest.isEqual(suppliedSecret.toByteArray(), internalSecret.toByteArray())
}
