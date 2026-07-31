package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.service.CatalogService
import com.pawsnearme.catalogservice.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID


class CatalogAccessDeniedException(message: String) : RuntimeException(message)

@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController(
    private val catalogService: CatalogService
) {

    private fun verifyProviderOwnership(providerId: UUID, xUserId: String?, xUserRole: String?) {
        if (xUserRole == "ADMIN") return
        if (xUserRole == "MERCHANT" && !xUserId.isNullOrBlank()) {
            if (catalogService.isProviderOwnedBy(providerId, UUID.fromString(xUserId))) return
        }
        throw CatalogAccessDeniedException("Access denied: merchant does not own provider $providerId")
    }

    // --- Offerings API ---

    @PostMapping("/offerings")
    fun createOffering(
        @Valid @RequestBody request: OfferingRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        verifyProviderOwnership(request.providerId!!, xUserId, role)
        val offering = Offering(
            providerId = request.providerId!!,
            name = request.name!!,
            description = request.description,
            category = request.category,
            price = request.price!!,
            imageUrl = request.imageUrl,
            status = request.status,
            stockQuantity = request.stockQuantity,
            sku = request.sku,
            durationMinutes = request.durationMinutes,
            barcode = request.barcode
        )
        val created = catalogService.createOffering(offering)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/offerings/{offeringId}")
    fun getOfferingById(@PathVariable offeringId: UUID): ResponseEntity<Offering> {
        val offering = catalogService.getOfferingById(offeringId)
        return ResponseEntity.ok(offering)
    }

    @GetMapping("/offerings")
    fun getOfferingsByProvider(@RequestParam providerId: UUID): ResponseEntity<List<Offering>> {
        val offerings = catalogService.getOfferingsByProvider(providerId)
        return ResponseEntity.ok(offerings)
    }

    @PutMapping("/offerings/{offeringId}")
    fun updateOffering(
        @PathVariable offeringId: UUID,
        @Valid @RequestBody request: OfferingRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        // SECURITY: Verify against the *existing* offering's providerId, NOT the client-supplied
        // request body. Without this check, a merchant could overwrite another merchant's offering
        // by sending their own providerId in the request body (IDOR vulnerability).
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)

        val offering = Offering(
            providerId = existing.providerId,   // lock to existing provider — client cannot reassign
            name = request.name!!,
            description = request.description,
            category = request.category,
            price = request.price!!,
            imageUrl = request.imageUrl,
            status = request.status,
            stockQuantity = request.stockQuantity,
            sku = request.sku,
            durationMinutes = request.durationMinutes,
            barcode = request.barcode
        )
        val updated = catalogService.updateOffering(offeringId, offering)
        return ResponseEntity.ok(updated)
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

    /**
     * Merchant/admin stock adjustments only. Service-to-service mutations must use
     * [InternalCatalogController] with X-Internal-Secret — never the gateway trust header.
     */
    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        val updated = catalogService.decrementStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
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
        val updated = catalogService.restoreStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
    }

    // --- Slots API ---

    @PostMapping("/slots")
    fun createSlot(
        @Valid @RequestBody request: SlotRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Slot> {
        val offering = catalogService.getOfferingById(request.offeringId!!)
        verifyProviderOwnership(offering.providerId, xUserId, role)
        val slot = Slot(
            offeringId = request.offeringId!!,
            slotStart = request.slotStart!!,
            slotEnd = request.slotEnd!!,
            status = request.status
        )
        val created = catalogService.createSlot(slot)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/slots")
    fun getSlotsByOffering(@RequestParam offeringId: UUID): ResponseEntity<List<Slot>> {
        val slots = catalogService.getSlotsByOffering(offeringId)
        return ResponseEntity.ok(slots)
    }

    @GetMapping("/slots/{slotId}")
    fun getSlot(@PathVariable slotId: UUID): ResponseEntity<Slot> {
        return try {
            ResponseEntity.ok(catalogService.getSlotById(slotId))
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }

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
        val updated = catalogService.updateSlotStatus(slotId, status)
        return ResponseEntity.ok(updated)
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

    // --- Barcode & Billing API ---

    @GetMapping("/offerings/by-barcode/{barcode}")
    fun getOfferingByBarcode(
        @PathVariable barcode: String,
        @RequestParam(required = false) storeId: UUID?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (xUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Unauthorized: user context missing"))
        }

        val ownerUserId = UUID.fromString(xUserId)
        val resolvedStoreId = storeId ?: run {
            val providers = catalogService.getProvidersByOwner(UUID.fromString(xUserId))
            if (providers.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "No providers found for owner user ID"))
            }
            providers[0].providerId
        }

        if (!catalogService.isProviderOwnedBy(resolvedStoreId, ownerUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied for store"))
        }

        return try {
            val offering = catalogService.getOfferingByBarcode(resolvedStoreId, barcode)
            ResponseEntity.ok(offering)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/bills")
    fun createBill(
        @Valid @RequestBody request: BillRequest,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (role != "MERCHANT" && role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied: role not authorized"))
        }
        if (xUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Unauthorized: user context missing"))
        }
        if (role == "MERCHANT" && !catalogService.isProviderOwnedBy(request.storeId!!, UUID.fromString(xUserId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied for store"))
        }
        return try {
            val authenticatedRequest = request.copy(staffId = UUID.fromString(xUserId))
            val response = catalogService.createBill(authenticatedRequest)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/bills/{id}")
    fun getBill(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        return try {
            val response = catalogService.getBillById(id)
            if (role == "MERCHANT") {
                if (xUserId == null || !catalogService.isProviderOwnedBy(response.bill.storeId, UUID.fromString(xUserId))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied for bill"))
                }
            } else if (role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied: role not authorized"))
            }
            ResponseEntity.ok(response)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/bills")
    fun getBillsByStore(
        @RequestParam storeId: UUID,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Any> {
        if (role == "MERCHANT") {
            if (xUserId == null || !catalogService.isProviderOwnedBy(storeId, UUID.fromString(xUserId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied for store"))
            }
        } else if (role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Access denied: role not authorized"))
        }
        val bills = catalogService.getBillsByStore(storeId)
        return ResponseEntity.ok(bills)
    }

    @ExceptionHandler(CatalogAccessDeniedException::class)
    fun handleAccessDenied(ex: CatalogAccessDeniedException): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
    }
}
