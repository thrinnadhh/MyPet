package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.service.CatalogService
import com.pawsnearme.catalogservice.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID


@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController(private val catalogService: CatalogService) {

    // --- Offerings API ---

    @PostMapping("/offerings")
    fun createOffering(@Valid @RequestBody request: OfferingRequest): ResponseEntity<Offering> {
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
        @Valid @RequestBody request: OfferingRequest
    ): ResponseEntity<Offering> {
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
        val updated = catalogService.updateOffering(offeringId, offering)
        return ResponseEntity.ok(updated)
    }


    @DeleteMapping("/offerings/{offeringId}")
    fun deleteOffering(@PathVariable offeringId: UUID): ResponseEntity<Unit> {
        catalogService.deleteOffering(offeringId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int
    ): ResponseEntity<Offering> {
        val updated = catalogService.decrementStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
    }

    // --- Slots API ---

    @PostMapping("/slots")
    fun createSlot(@Valid @RequestBody request: SlotRequest): ResponseEntity<Slot> {
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
        @RequestParam status: SlotStatus
    ): ResponseEntity<Slot> {
        val updated = catalogService.updateSlotStatus(slotId, status)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/slots/{slotId}")
    fun deleteSlot(@PathVariable slotId: UUID): ResponseEntity<Unit> {
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
            val response = catalogService.createBill(request)
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
}

