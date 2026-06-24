package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class CatalogService(
    private val offeringRepository: OfferingRepository,
    private val slotRepository: SlotRepository,
    private val providerRepository: ProviderRepository
) {

    fun createOffering(offering: Offering): Offering {
        val provider = providerRepository.findById(offering.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${offering.providerId} not found") }

        validateOfferingFields(offering, provider.fulfillmentType)
        return offeringRepository.save(offering)
    }

    fun updateOffering(offeringId: UUID, updated: Offering): Offering {
        val existing = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }

        val provider = providerRepository.findById(existing.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${existing.providerId} not found") }

        validateOfferingFields(updated, provider.fulfillmentType)

        existing.name = updated.name
        existing.description = updated.description
        existing.category = updated.category
        existing.price = updated.price
        existing.imageUrl = updated.imageUrl
        existing.status = updated.status
        existing.stockQuantity = updated.stockQuantity
        existing.sku = updated.sku
        existing.durationMinutes = updated.durationMinutes

        return offeringRepository.save(existing)
    }

    fun getOfferingById(offeringId: UUID): Offering {
        return offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
    }

    fun getOfferingsByProvider(providerId: UUID): List<Offering> {
        return offeringRepository.findByProviderId(providerId)
    }

    fun deleteOffering(offeringId: UUID) {
        if (!offeringRepository.existsById(offeringId)) {
            throw NoSuchElementException("Offering with ID $offeringId not found")
        }
        offeringRepository.deleteById(offeringId)
    }

    // --- Slot Operations ---

    fun createSlot(slot: Slot): Slot {
        val offering = offeringRepository.findById(slot.offeringId)
            .orElseThrow { IllegalArgumentException("Offering with ID ${slot.offeringId} not found") }

        if (offering.durationMinutes == null) {
            throw IllegalArgumentException("Cannot create slots for a DELIVERY (product) offering")
        }

        if (!slot.slotEnd.isAfter(slot.slotStart)) {
            throw IllegalArgumentException("Slot end time must be after slot start time")
        }

        return slotRepository.save(slot)
    }

    fun getSlotsByOffering(offeringId: UUID): List<Slot> {
        if (!offeringRepository.existsById(offeringId)) {
            throw NoSuchElementException("Offering with ID $offeringId not found")
        }
        return slotRepository.findByOfferingId(offeringId)
    }

    fun updateSlotStatus(slotId: UUID, status: SlotStatus): Slot {
        val slot = slotRepository.findById(slotId)
            .orElseThrow { NoSuchElementException("Slot with ID $slotId not found") }
        slot.status = status
        return slotRepository.save(slot)
    }

    fun deleteSlot(slotId: UUID) {
        if (!slotRepository.existsById(slotId)) {
            throw NoSuchElementException("Slot with ID $slotId not found")
        }
        slotRepository.deleteById(slotId)
    }

    // --- Private Helpers ---

    private fun validateOfferingFields(offering: Offering, fulfillmentType: String) {
        if (fulfillmentType == "DELIVERY") {
            if (offering.stockQuantity == null) {
                throw IllegalArgumentException("DELIVERY fulfillment offerings must specify a stock quantity")
            }
            if (offering.durationMinutes != null) {
                throw IllegalArgumentException("DELIVERY fulfillment offerings cannot specify a duration")
            }
        } else if (fulfillmentType == "APPOINTMENT") {
            if (offering.durationMinutes == null) {
                throw IllegalArgumentException("APPOINTMENT fulfillment offerings must specify a duration in minutes")
            }
            if (offering.stockQuantity != null) {
                throw IllegalArgumentException("APPOINTMENT fulfillment offerings cannot specify a stock quantity")
            }
        } else {
            throw IllegalArgumentException("Unsupported fulfillment type: $fulfillmentType")
        }
    }
}
