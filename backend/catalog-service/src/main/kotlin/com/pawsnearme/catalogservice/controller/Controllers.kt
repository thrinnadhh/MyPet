package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.dto.*
import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.service.CatalogService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

class CatalogAccessDeniedException(message: String) : RuntimeException(message)

@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController(
    private val catalogService: CatalogService,
) {
    private fun verifyProviderOwnership(providerId: UUID, xUserId: String?, xUserRole: String?) {
        if (xUserRole == "ADMIN") return
        if (xUserRole == "MERCHANT" && !xUserId.isNullOrBlank()) {
            if (catalogService.isProviderOwnedBy(providerId, UUID.fromString(xUserId))) return
        }
        throw CatalogAccessDeniedException("Access denied: merchant does not own provider $providerId")
    }

    @PostMapping("/offerings")
    fun createOffering(
        @Valid @RequestBody request: OfferingRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        verifyProviderOwnership(request.providerId!!, xUserId, role)
        val offering = Offering(
            providerId = request.providerId,
            name = request.name!!,
            description = request.description,
            category = request.category,
            price = request.price!!,
            imageUrl = request.imageUrl,
            status = request.status,
            stockQuantity = request.stockQuantity,
            sku = request.sku,
            durationMinutes = request.durationMinutes,
            barcode = request.barcode,
            gstRate = request.gstRate ?: java.math.BigDecimal("18.00")
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createOffering(offering))
    }

    @GetMapping("/offerings/{offeringId}")
    fun getOfferingById(@PathVariable offeringId: UUID): ResponseEntity<Offering> =
        ResponseEntity.ok(catalogService.getOfferingById(offeringId))

    @GetMapping("/offerings")
    fun getOfferingsByProvider(@RequestParam providerId: UUID): ResponseEntity<List<Offering>> =
        ResponseEntity.ok(catalogService.getOfferingsByProvider(providerId))

    @PutMapping("/offerings/{offeringId}")
    fun updateOffering(
        @PathVariable offeringId: UUID,
        @Valid @RequestBody request: OfferingRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        val offering = Offering(
            providerId = existing.providerId,
            name = request.name!!,
            description = request.description,
            category = request.category,
            price = request.price!!,
            imageUrl = request.imageUrl,
            status = request.status,
            stockQuantity = request.stockQuantity,
            sku = request.sku,
            durationMinutes = request.durationMinutes,
            barcode = request.barcode,
            gstRate = request.gstRate ?: existing.gstRate
        )
        return ResponseEntity.ok(catalogService.updateOffering(offeringId, offering))
    }

    @DeleteMapping("/offerings/{offeringId}")
    fun deleteOffering(
        @PathVariable offeringId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Unit> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        catalogService.deleteOffering(offeringId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        return ResponseEntity.ok(catalogService.decrementStock(offeringId, quantity))
    }

    @PutMapping("/offerings/{offeringId}/restore-stock")
    fun restoreStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        return ResponseEntity.ok(catalogService.restoreStock(offeringId, quantity))
    }

    @PostMapping("/slots")
    fun createSlot(
        @Valid @RequestBody request: SlotRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Slot> {
        val offering = catalogService.getOfferingById(request.offeringId!!)
        verifyProviderOwnership(offering.providerId, xUserId, role)
        val slot = Slot(
            offeringId = request.offeringId,
            slotStart = request.slotStart!!,
            slotEnd = request.slotEnd!!,
            status = request.status
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createSlot(slot))
    }

    @GetMapping("/slots")
    fun getSlotsByOffering(@RequestParam offeringId: UUID): ResponseEntity<List<Slot>> =
        ResponseEntity.ok(catalogService.getSlotsByOffering(offeringId))

    @GetMapping("/slots/{slotId}")
    fun getSlot(@PathVariable slotId: UUID): ResponseEntity<Slot> =
        ResponseEntity.ok(catalogService.getSlotById(slotId))

    @PutMapping("/slots/{slotId}/status")
    fun updateSlotStatus(
        @PathVariable slotId: UUID,
        @RequestParam status: SlotStatus,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Slot> {
        val slot = catalogService.getSlotById(slotId)
        val offering = catalogService.getOfferingById(slot.offeringId)
        verifyProviderOwnership(offering.providerId, xUserId, role)
        return ResponseEntity.ok(catalogService.updateSlotStatus(slotId, status))
    }

    @DeleteMapping("/slots/{slotId}")
    fun deleteSlot(
        @PathVariable slotId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Unit> {
        val slot = catalogService.getSlotById(slotId)
        val offering = catalogService.getOfferingById(slot.offeringId)
        verifyProviderOwnership(offering.providerId, xUserId, role)
        catalogService.deleteSlot(slotId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/offerings/by-barcode")
    fun getOfferingByBarcodeQuery(
        @RequestParam storeId: UUID,
        @RequestParam barcode: String,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> = resolveOfferingByBarcode(storeId, barcode, role, xUserId)

    /** Compatibility route retained for existing hardware integrations. */
    @GetMapping("/offerings/by-barcode/{barcode}")
    fun getOfferingByBarcodePath(
        @PathVariable barcode: String,
        @RequestParam(required = false) storeId: UUID?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Unauthorized: user context missing"))
        }
        val resolvedStoreId = storeId ?: catalogService.getProvidersByOwner(UUID.fromString(xUserId))
            .firstOrNull()
            ?.providerId
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "No providers found for authenticated user"))
        return resolveOfferingByBarcode(resolvedStoreId, barcode, role, xUserId)
    }

    private fun resolveOfferingByBarcode(
        storeId: UUID,
        barcode: String,
        role: String?,
        xUserId: String?
    ): ResponseEntity<Any> {
        if (role != "MERCHANT" && role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access denied: role not authorized"))
        }
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Unauthorized: user context missing"))
        }
        if (role == "MERCHANT" && !catalogService.isProviderOwnedBy(storeId, UUID.fromString(xUserId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access denied for store"))
        }
        return ResponseEntity.ok(catalogService.getOfferingByBarcode(storeId, barcode))
    }

    @PostMapping("/bills")
    fun createBill(
        @Valid @RequestBody request: BillRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (role != "MERCHANT" && role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access denied: role not authorized"))
        }
        if (xUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Unauthorized: user context missing"))
        }
        if (role == "MERCHANT" && !catalogService.isProviderOwnedBy(request.storeId!!, UUID.fromString(xUserId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access denied for store"))
        }

        val authenticatedRequest = request.copy(staffId = UUID.fromString(xUserId))
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createBill(authenticatedRequest))
    }

    @GetMapping("/bills/{id}")
    fun getBill(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        val response = catalogService.getBillById(id)
        if (role == "MERCHANT") {
            if (xUserId.isNullOrBlank() || !catalogService.isProviderOwnedBy(response.bill.storeId, UUID.fromString(xUserId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "Access denied for bill"))
            }
        } else if (role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access denied: role not authorized"))
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/bills")
    fun getBillsByStore(
        @RequestParam storeId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (role == "MERCHANT") {
            if (xUserId.isNullOrBlank() || !catalogService.isProviderOwnedBy(storeId, UUID.fromString(xUserId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "Access denied for store"))
            }
        } else if (role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Access denied: role not authorized"))
        }
        return ResponseEntity.ok(catalogService.getBillsByStore(storeId))
    }

    @ExceptionHandler(CatalogAccessDeniedException::class)
    fun handleAccessDenied(ex: CatalogAccessDeniedException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
}
