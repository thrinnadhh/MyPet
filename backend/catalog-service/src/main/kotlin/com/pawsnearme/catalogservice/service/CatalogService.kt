package com.pawsnearme.catalogservice.service

import org.slf4j.LoggerFactory
import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.repository.*
import com.pawsnearme.catalogservice.dto.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import org.springframework.data.redis.core.StringRedisTemplate
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.math.BigDecimal
import java.time.Instant

@Service
@Transactional(readOnly = true)
class CatalogService(
    private val offeringRepository: OfferingRepository,
    private val slotRepository: SlotRepository,
    private val providerRepository: ProviderRepository,
    private val billRepository: BillRepository,
    private val billItemRepository: BillItemRepository,
    private val stringRedisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(CatalogService::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()


    @Transactional
    fun createOffering(offering: Offering): Offering {
        val provider = providerRepository.findById(offering.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${offering.providerId} not found") }

        validateOfferingFields(offering, provider.fulfillmentType)
        return offeringRepository.save(offering)
    }

    @Transactional
    fun updateOffering(offeringId: UUID, updated: Offering): Offering {
        val existing = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }

        val provider = providerRepository.findById(existing.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${existing.providerId} not found") }

        validateOfferingFields(updated, provider.fulfillmentType)

        // Evict cache
        if (existing.barcode != null) {
            try {
                stringRedisTemplate.delete("barcodes:cache:${existing.providerId}:${existing.barcode}")
            } catch (e: Exception) {
                logger.warn("Redis cache eviction failed: {}", e.message, e)
            }
        }

        existing.name = updated.name
        existing.description = updated.description
        existing.category = updated.category
        existing.price = updated.price
        existing.imageUrl = updated.imageUrl
        existing.status = updated.status
        existing.stockQuantity = updated.stockQuantity
        existing.sku = updated.sku
        existing.durationMinutes = updated.durationMinutes
        existing.barcode = updated.barcode

        return offeringRepository.save(existing)
    }

    fun getOfferingById(offeringId: UUID): Offering {
        return offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
    }

    fun getOfferingsByProvider(providerId: UUID): List<Offering> {
        return offeringRepository.findByProviderId(providerId)
    }

    @Transactional
    fun deleteOffering(offeringId: UUID) {
        val existing = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
        
        if (existing.barcode != null) {
            try {
                stringRedisTemplate.delete("barcodes:cache:${existing.providerId}:${existing.barcode}")
            } catch (e: Exception) {
                logger.warn("Redis cache eviction failed: {}", e.message, e)
            }
        }
        
        offeringRepository.deleteById(offeringId)
    }


    @Transactional
    fun decrementStock(offeringId: UUID, quantity: Int): Offering {
        if (quantity <= 0) {
            throw IllegalArgumentException("Quantity must be greater than zero")
        }
        val offering = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
        if (offering.stockQuantity == null) {
            throw IllegalArgumentException("Offering does not support stock tracking")
        }
        if (offering.stockQuantity!! < quantity) {
            throw IllegalArgumentException("Insufficient stock quantity for offering $offeringId")
        }
        val updatedRows = offeringRepository.decrementStockIfAvailable(offeringId, offering.providerId, quantity)
        if (updatedRows != 1) {
            throw IllegalArgumentException("Insufficient stock quantity for offering $offeringId")
        }
        offering.stockQuantity = offering.stockQuantity!! - quantity
        return offering
    }

    @Transactional
    fun restoreStock(offeringId: UUID, quantity: Int): Offering {
        if (quantity <= 0) {
            throw IllegalArgumentException("Quantity must be greater than zero")
        }
        val offering = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
        if (offering.stockQuantity == null) {
            throw IllegalArgumentException("Offering does not support stock tracking")
        }
        val updatedRows = offeringRepository.incrementStockIfTracked(offeringId, quantity)
        if (updatedRows != 1) {
            throw IllegalStateException("Unable to restore stock for offering $offeringId")
        }
        offering.stockQuantity = offering.stockQuantity!! + quantity
        return offering
    }

    // --- Slot Operations ---

    @Transactional
    fun createSlot(slot: Slot): Slot {
        val offering = offeringRepository.findById(slot.offeringId)
            .orElseThrow { IllegalArgumentException("Offering with ID ${slot.offeringId} not found") }

        // Only APPOINTMENT-type offerings support time slots
        val offeringProvider = providerRepository.findById(offering.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${offering.providerId} not found") }
        if (offeringProvider.fulfillmentType != FulfillmentType.APPOINTMENT) {
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

    fun getSlotById(slotId: UUID): Slot {
        return slotRepository.findById(slotId)
            .orElseThrow { NoSuchElementException("Slot with ID $slotId not found") }
    }

    @Transactional
    fun updateSlotStatus(slotId: UUID, status: SlotStatus): Slot {
        val slot = slotRepository.findById(slotId)
            .orElseThrow { NoSuchElementException("Slot with ID $slotId not found") }
        slot.status = status
        return slotRepository.save(slot)
    }

    @Transactional
    fun deleteSlot(slotId: UUID) {
        if (!slotRepository.existsById(slotId)) {
            throw NoSuchElementException("Slot with ID $slotId not found")
        }
        slotRepository.deleteById(slotId)
    }

    // --- Private Helpers ---

    private fun validateOfferingFields(offering: Offering, fulfillmentType: FulfillmentType) {
        when (fulfillmentType) {
            FulfillmentType.DELIVERY -> {
                if (offering.stockQuantity == null) {
                    throw IllegalArgumentException("DELIVERY fulfillment offerings must specify a stock quantity")
                }
                if (offering.durationMinutes != null) {
                    throw IllegalArgumentException("DELIVERY fulfillment offerings cannot specify a duration")
                }
            }
            FulfillmentType.APPOINTMENT -> {
                if (offering.durationMinutes == null) {
                    throw IllegalArgumentException("APPOINTMENT fulfillment offerings must specify a duration in minutes")
                }
                if (offering.stockQuantity != null) {
                    throw IllegalArgumentException("APPOINTMENT fulfillment offerings cannot specify a stock quantity")
                }
            }
        }
    }

    fun getOfferingByBarcode(providerId: UUID, barcode: String): Offering {
        val cacheKey = "barcodes:cache:$providerId:$barcode"
        try {
            val cachedJson = stringRedisTemplate.opsForValue().get(cacheKey)
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, Offering::class.java)
            }
        } catch (e: Exception) {
            logger.warn("Redis cache read failed: {}", e.message, e)
        }

        val offering = offeringRepository.findByProviderIdAndBarcode(providerId, barcode)
            ?: throw NoSuchElementException("Offering with barcode $barcode not found for provider $providerId")

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(offering), java.time.Duration.ofMinutes(5))
        } catch (e: Exception) {
            logger.warn("Redis cache write failed: {}", e.message, e)
        }

        return offering
    }

    @Transactional
    fun createBill(request: BillRequest): BillResponse {
        val existingBill = billRepository.findByIdempotencyKey(request.idempotencyKey!!)
        if (existingBill != null) {
            val items = billItemRepository.findByBillId(existingBill.id!!)
            return BillResponse(existingBill, items, emptyList())
        }

        // Save bill in DRAFT state first to establish transaction
        val bill = Bill(
            storeId = request.storeId!!,
            staffId = request.staffId!!,
            status = "DRAFT",
            subtotal = BigDecimal.ZERO,
            totalDiscount = BigDecimal.ZERO,
            tax = BigDecimal.ZERO,
            grandTotal = BigDecimal.ZERO,
            idempotencyKey = request.idempotencyKey
        )
        val savedBill = billRepository.save(bill)

        val successfulItems = mutableListOf<BillItem>()
        val failedItems = mutableListOf<FailedBillItem>()
        var calculatedSubtotal = BigDecimal.ZERO
        var calculatedDiscount = BigDecimal.ZERO

        for (item in request.items) {
            try {
                val offering = offeringRepository.findById(item.productId!!)
                    .orElseThrow { IllegalArgumentException("Product ${item.productId} not found") }
                if (offering.stockQuantity == null) {
                    throw IllegalArgumentException("Offering is not a physical product")
                }
                if (offering.stockQuantity!! < item.quantity!!) {
                    throw IllegalStateException("Out of stock")
                }

                val updatedRows = offeringRepository.decrementStockIfAvailable(item.productId, request.storeId, item.quantity)
                if (updatedRows != 1) {
                    throw IllegalStateException("Out of stock")
                }
                offering.stockQuantity = offering.stockQuantity!! - item.quantity

                val billItem = BillItem(
                    billId = savedBill.id!!,
                    productId = item.productId,
                    barcodeScanned = item.barcodeScanned!!,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice!!,
                    discountAmount = item.discountAmount!!,
                    discountType = item.discountType!!
                )
                billItemRepository.save(billItem)
                successfulItems.add(billItem)

                val lineTotal = item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))
                calculatedSubtotal = calculatedSubtotal.add(lineTotal)
                calculatedDiscount = calculatedDiscount.add(item.discountAmount)
            } catch (e: Exception) {
                failedItems.add(FailedBillItem(item.productId!!, item.barcodeScanned ?: "", e.message ?: "Unknown error"))
            }
        }

        // Compute final amounts
        val finalStatus = if (failedItems.isEmpty()) "SYNCED" else "FINALIZED"
        val taxRate = BigDecimal("0.18")
        val finalSubtotal = calculatedSubtotal
        val finalDiscount = calculatedDiscount
        val finalTax = finalSubtotal.subtract(finalDiscount).multiply(taxRate).max(BigDecimal.ZERO)
        val finalGrandTotal = finalSubtotal.subtract(finalDiscount).add(finalTax).max(BigDecimal.ZERO)

        savedBill.status = finalStatus
        savedBill.subtotal = finalSubtotal
        savedBill.totalDiscount = finalDiscount
        savedBill.tax = finalTax
        savedBill.grandTotal = finalGrandTotal
        savedBill.syncedAt = if (finalStatus == "SYNCED") Instant.now() else null
        
        val finalSavedBill = billRepository.save(savedBill)

        return BillResponse(finalSavedBill, successfulItems, failedItems)
    }

    fun getBillById(id: UUID): BillResponse {
        val bill = billRepository.findById(id).orElseThrow { NoSuchElementException("Bill $id not found") }
        val items = billItemRepository.findByBillId(id)
        return BillResponse(bill, items, emptyList())
    }

    fun getBillsByStore(storeId: UUID): List<Bill> {
        return billRepository.findByStoreId(storeId)
    }

    fun getProvidersByOwner(ownerUserId: UUID): List<Provider> {
        return providerRepository.findByOwnerUserId(ownerUserId)
    }

    fun isProviderOwnedBy(providerId: UUID, ownerUserId: UUID): Boolean {
        return providerRepository.existsByProviderIdAndOwnerUserId(providerId, ownerUserId)
    }
}
