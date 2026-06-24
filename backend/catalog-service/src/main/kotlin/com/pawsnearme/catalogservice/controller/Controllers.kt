package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.service.CatalogService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID


@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController(private val catalogService: CatalogService) {

    // --- Offerings API ---

    @PostMapping("/offerings")
    fun createOffering(@RequestBody offering: Offering): ResponseEntity<Offering> {
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
        @RequestBody offering: Offering
    ): ResponseEntity<Offering> {
        val updated = catalogService.updateOffering(offeringId, offering)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/offerings/{offeringId}")
    fun deleteOffering(@PathVariable offeringId: UUID): ResponseEntity<Unit> {
        catalogService.deleteOffering(offeringId)
        return ResponseEntity.noContent().build()
    }

    // --- Slots API ---

    @PostMapping("/slots")
    fun createSlot(@RequestBody slot: Slot): ResponseEntity<Slot> {
        val created = catalogService.createSlot(slot)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/slots")
    fun getSlotsByOffering(@RequestParam offeringId: UUID): ResponseEntity<List<Slot>> {
        val slots = catalogService.getSlotsByOffering(offeringId)
        return ResponseEntity.ok(slots)
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
}

