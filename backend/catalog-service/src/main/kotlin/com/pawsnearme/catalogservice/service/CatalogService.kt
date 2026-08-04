package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.dto.*
import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.repository.*
import com.pawsnearme.catalogservice.support.BarcodeConflictException
import com.pawsnearme.catalogservice.support.BarcodeSupport
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

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

    @Transactional
    fun createOffering(offering: Offering): Offering {
        val provider = providerRepository.findById(offering.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${offering.providerId} not found") }

        normalizeOfferingBarcode(offering)
        validateOfferingFields(offering, provider.fulfillmentType)
        ensureBarcodeAvailable(offering.providerId, offering.barcode, null)

        return saveOffering(offering)
    }

    @Transactional
    fun updateOffering(offeringId: UUID, updated: Offering): Offering {
        val existing = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }

        val provider = providerRepository.findById(existing.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${existing.providerId} not found") }

        updated.providerId = existing.providerId
        normalizeOfferingBarcode(updated)
        validateOfferingFields(updated, provider.fulfillmentType)
        ensureBarcodeAvailable(existing.providerId, updated.barcode, offeringId)

        val previousBarcode = existing.barcode
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

        val saved = saveOffering(existing)
        evictBarcodeCache(existing.providerId, previousBarcode)
        evictBarcodeCache(existing.providerId, saved.barcode)
        return saved
    }

    fun getOfferingById(offeringId: UUID): Offering =
        offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }

    fun getOfferingsByProvider(providerId: UUID): List<Offering> =
        offeringRepository.findByProviderId(providerId)

    @Transactional
    fun deleteOffering(offeringId: UUID) {
        val existing = getOfferingById(offeringId)
        evictBarcodeCache(existing.providerId, existing.barcode)
        offeringRepository.deleteById(offeringId)
    }

    @Transactional
    fun decrementStock(offeringId: UUID, quantity: Int): Offering {
        require(quantity > 0) { "Quantity must be greater than zero" }
        val offering = getOfferingById(offeringId)
        val currentStock = offering.stockQuantity
            ?: throw IllegalArgumentException("Offering does not support stock tracking")
        if (currentStock < quantity) {
            throw IllegalArgumentException("Insufficient stock quantity for offering $offeringId")
        }

        val updatedRows = offeringRepository.decrementStockIfAvailable(offeringId, offering.providerId, quantity)
        if (updatedRows != 1) {
            throw IllegalArgumentException("Insufficient stock quantity for offering $offeringId")
        }
        offering.stockQuantity = currentStock - quantity
        evictBarcodeCache(offering.providerId, offering.barcode)
        return offering
    }

    @Transactional
    fun restoreStock(offeringId: UUID, quantity: Int): Offering {
        require(quantity > 0) { "Quantity must be greater than zero" }
        val offering = getOfferingById(offeringId)
        val currentStock = offering.stockQuantity
            ?: throw IllegalArgumentException("Offering does not support stock tracking")

        val updatedRows = offeringRepository.incrementStockIfTracked(offeringId, quantity)
        if (updatedRows != 1) {
            throw IllegalStateException("Unable to restore stock for offering $offeringId")
        }
        offering.stockQuantity = currentStock + quantity
        evictBarcodeCache(offering.providerId, offering.barcode)
        return offering
    }

    @Transactional
    fun createSlot(slot: Slot): Slot {
        val offering = offeringRepository.findById(slot.offeringId)
            .orElseThrow { IllegalArgumentException("Offering with ID ${slot.offeringId} not found") }
        val offeringProvider = providerRepository.findById(offering.providerId)
            .orElseThrow { IllegalArgumentException("Provider with ID ${offering.providerId} not found") }
        if (offeringProvider.fulfillmentType != FulfillmentType.APPOINTMENT) {
            throw IllegalArgumentException("Cannot create slots for a DELIVERY (product) offering")
        }
        require(slot.slotEnd.isAfter(slot.slotStart)) { "Slot end time must be after slot start time" }
        return slotRepository.save(slot)
    }

    fun getSlotsByOffering(offeringId: UUID): List<Slot> {
        if (!offeringRepository.existsById(offeringId)) {
            throw NoSuchElementException("Offering with ID $offeringId not found")
        }
        return slotRepository.findByOfferingId(offeringId)
    }

    fun getSlotById(slotId: UUID): Slot =
        slotRepository.findById(slotId)
            .orElseThrow { NoSuchElementException("Slot with ID $slotId not found") }

    @Transactional
    fun updateSlotStatus(slotId: UUID, status: SlotStatus): Slot {
        val slot = getSlotById(slotId)
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

    fun getOfferingByBarcode(providerId: UUID, rawBarcode: String): Offering {
        val barcode = BarcodeSupport.requireBarcode(rawBarcode)
        val candidates = BarcodeSupport.lookupCandidates(barcode)
        val cacheKey = barcodeCacheKey(providerId, barcode)

        try {
            val cachedId = stringRedisTemplate.opsForValue().get(cacheKey)
            if (!cachedId.isNullOrBlank()) {
                val cachedOffering = runCatching { offeringRepository.findById(UUID.fromString(cachedId)) }
                    .getOrNull()
                    ?.orElse(null)
                if (
                    cachedOffering != null &&
                    cachedOffering.providerId == providerId &&
                    cachedOffering.barcode != null &&
                    BarcodeSupport.lookupCandidates(cachedOffering.barcode!!).any(candidates::contains)
                ) {
                    return cachedOffering
                }
                stringRedisTemplate.delete(cacheKey)
            }
        } catch (exception: Exception) {
            logger.warn("Barcode cache read failed: {}", exception.message)
        }

        val offering = offeringRepository.findFirstByProviderIdAndBarcodeIn(providerId, candidates)
            ?: throw NoSuchElementException("Offering with barcode $barcode not found for provider $providerId")

        try {
            offering.offeringId?.let {
                stringRedisTemplate.opsForValue().set(cacheKey, it.toString(), Duration.ofMinutes(5))
            }
        } catch (exception: Exception) {
            logger.warn("Barcode cache write failed: {}", exception.message)
        }
        return offering
    }

    @Transactional
    fun createBill(request: BillRequest): BillResponse {
        val idempotencyKey = request.idempotencyKey
            ?: throw IllegalArgumentException("Idempotency key is required")
        val existingBill = billRepository.findByIdempotencyKey(idempotencyKey)
        if (existingBill != null) {
            val items = billItemRepository.findByBillId(existingBill.id!!)
            return BillResponse(existingBill, items, emptyList())
        }

        val storeId = request.storeId ?: throw IllegalArgumentException("Store ID is required")
        val staffId = request.staffId ?: throw IllegalArgumentException("Staff ID is required")
        val savedBill = billRepository.save(
            Bill(
                storeId = storeId,
                staffId = staffId,
                status = "DRAFT",
                subtotal = BigDecimal.ZERO,
                totalDiscount = BigDecimal.ZERO,
                tax = BigDecimal.ZERO,
                grandTotal = BigDecimal.ZERO,
                idempotencyKey = idempotencyKey
            )
        )

        val successfulItems = mutableListOf<BillItem>()
        val failedItems = mutableListOf<FailedBillItem>()
        var calculatedSubtotal = BigDecimal.ZERO
        var calculatedDiscount = BigDecimal.ZERO

        request.items.forEach { item ->
            val productId = item.productId ?: return@forEach
            try {
                val quantity = item.quantity ?: throw IllegalArgumentException("Quantity is required")
                require(quantity > 0) { "Quantity must be at least 1" }

                val offering = offeringRepository.findById(productId)
                    .orElseThrow { IllegalArgumentException("Product $productId not found") }
                if (offering.providerId != storeId) {
                    throw IllegalArgumentException("Product does not belong to the selected store")
                }
                if (offering.status != OfferingStatus.ACTIVE) {
                    throw IllegalStateException("Product is not active")
                }
                val currentStock = offering.stockQuantity
                    ?: throw IllegalArgumentException("Offering is not a physical product")
                if (currentStock < quantity) throw IllegalStateException("Out of stock")

                val catalogBarcode = offering.barcode
                    ?: throw IllegalArgumentException("Product does not have a barcode")
                val scannedBarcode = BarcodeSupport.requireBarcode(item.barcodeScanned)
                val catalogCandidates = BarcodeSupport.lookupCandidates(catalogBarcode)
                if (BarcodeSupport.lookupCandidates(scannedBarcode).none(catalogCandidates::contains)) {
                    throw IllegalArgumentException("Scanned barcode does not match the selected product")
                }

                val serverUnitPrice = offering.price
                val lineTotal = serverUnitPrice.multiply(BigDecimal.valueOf(quantity.toLong()))
                val discount = item.discountAmount
                    ?: throw IllegalArgumentException("Discount amount is required")
                require(discount >= BigDecimal.ZERO) { "Discount amount must be non-negative" }
                require(discount <= lineTotal) { "Discount cannot exceed the product line total" }

                val updatedRows = offeringRepository.decrementStockIfAvailable(productId, storeId, quantity)
                if (updatedRows != 1) throw IllegalStateException("Out of stock")
                offering.stockQuantity = currentStock - quantity
                evictBarcodeCache(storeId, catalogBarcode)

                val billItem = billItemRepository.save(
                    BillItem(
                        billId = savedBill.id!!,
                        productId = productId,
                        barcodeScanned = BarcodeSupport.requireBarcode(catalogBarcode),
                        quantity = quantity,
                        unitPrice = serverUnitPrice,
                        discountAmount = discount,
                        discountType = item.discountType ?: "NONE"
                    )
                )
                successfulItems += billItem
                calculatedSubtotal = calculatedSubtotal.add(lineTotal)
                calculatedDiscount = calculatedDiscount.add(discount)
            } catch (exception: Exception) {
                failedItems += FailedBillItem(
                    productId = productId,
                    barcode = BarcodeSupport.normalize(item.barcodeScanned) ?: "",
                    reason = exception.message ?: "Unknown error"
                )
            }
        }

        if (successfulItems.isEmpty()) {
            val reason = failedItems.firstOrNull()?.reason ?: "No valid bill items were supplied"
            throw IllegalArgumentException("No bill items could be finalized: $reason")
        }

        val finalStatus = if (failedItems.isEmpty()) "SYNCED" else "FINALIZED"
        val finalTax = calculatedSubtotal
            .subtract(calculatedDiscount)
            .multiply(BigDecimal("0.18"))
            .max(BigDecimal.ZERO)
        val finalGrandTotal = calculatedSubtotal
            .subtract(calculatedDiscount)
            .add(finalTax)
            .max(BigDecimal.ZERO)

        savedBill.status = finalStatus
        savedBill.subtotal = calculatedSubtotal
        savedBill.totalDiscount = calculatedDiscount
        savedBill.tax = finalTax
        savedBill.grandTotal = finalGrandTotal
        savedBill.syncedAt = if (finalStatus == "SYNCED") Instant.now() else null

        return BillResponse(
            bill = billRepository.save(savedBill),
            successfulItems = successfulItems,
            failedItems = failedItems
        )
    }

    fun getBillById(id: UUID): BillResponse {
        val bill = billRepository.findById(id)
            .orElseThrow { NoSuchElementException("Bill $id not found") }
        return BillResponse(bill, billItemRepository.findByBillId(id), emptyList())
    }

    fun getBillsByStore(storeId: UUID): List<Bill> = billRepository.findByStoreId(storeId)

    fun getProvidersByOwner(ownerUserId: UUID): List<Provider> =
        providerRepository.findByOwnerUserId(ownerUserId)

    fun isProviderOwnedBy(providerId: UUID, ownerUserId: UUID): Boolean =
        providerRepository.existsByProviderIdAndOwnerUserId(providerId, ownerUserId)

    private fun normalizeOfferingBarcode(offering: Offering) {
        offering.barcode = BarcodeSupport.normalize(offering.barcode)
    }

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
                if (offering.barcode != null) {
                    throw IllegalArgumentException("APPOINTMENT fulfillment offerings cannot specify a barcode")
                }
            }
        }
    }

    private fun ensureBarcodeAvailable(providerId: UUID, barcode: String?, ignoredOfferingId: UUID?) {
        if (barcode == null) return
        val existing = offeringRepository.findFirstByProviderIdAndBarcodeIn(
            providerId,
            BarcodeSupport.lookupCandidates(barcode)
        )
        if (existing != null && existing.offeringId != ignoredOfferingId) {
            throw BarcodeConflictException("Barcode $barcode already belongs to another offering for this provider")
        }
    }

    private fun saveOffering(offering: Offering): Offering = try {
        offeringRepository.save(offering)
    } catch (exception: DataIntegrityViolationException) {
        throw BarcodeConflictException(
            offering.barcode?.let { "Barcode $it already belongs to another offering for this provider" }
                ?: "Offering conflicts with an existing catalog record"
        )
    }

    private fun barcodeCacheKey(providerId: UUID, barcode: String): String =
        "barcodes:cache:$providerId:${BarcodeSupport.requireBarcode(barcode)}"

    private fun evictBarcodeCache(providerId: UUID, rawBarcode: String?) {
        val barcode = BarcodeSupport.normalize(rawBarcode) ?: return
        BarcodeSupport.lookupCandidates(barcode).forEach { candidate ->
            try {
                stringRedisTemplate.delete(barcodeCacheKey(providerId, candidate))
            } catch (exception: Exception) {
                logger.warn("Barcode cache eviction failed: {}", exception.message)
            }
        }
    }
}
