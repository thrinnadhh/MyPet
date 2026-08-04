package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.dto.BillItemRequest
import com.pawsnearme.catalogservice.dto.BillRequest
import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.repository.*
import com.pawsnearme.catalogservice.support.BarcodeConflictException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CatalogServiceTests {
    private val offeringRepository: OfferingRepository = mock()
    private val slotRepository: SlotRepository = mock()
    private val providerRepository: ProviderRepository = mock()
    private val billRepository: BillRepository = mock()
    private val billItemRepository: BillItemRepository = mock()
    private val stringRedisTemplate: StringRedisTemplate = mock()

    private val catalogService = CatalogService(
        offeringRepository,
        slotRepository,
        providerRepository,
        billRepository,
        billItemRepository,
        stringRedisTemplate
    )

    @Test
    fun `createOffering canonicalizes leading zero EAN13 to UPC-A`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId))
            .thenReturn(Optional.of(Provider(providerId, FulfillmentType.DELIVERY)))
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.arguments[0] as Offering }

        val created = catalogService.createOffering(
            Offering(
                providerId = providerId,
                name = "Dog Food",
                price = BigDecimal("499.00"),
                stockQuantity = 50,
                barcode = "0 123456789012"
            )
        )

        assertEquals("123456789012", created.barcode)
        assertEquals(50, created.stockQuantity)
    }

    @Test
    fun `createOffering rejects duplicate provider barcode`() {
        val providerId = UUID.randomUUID()
        val barcode = "123456789012"
        whenever(providerRepository.findById(providerId))
            .thenReturn(Optional.of(Provider(providerId, FulfillmentType.DELIVERY)))
        whenever(offeringRepository.findFirstByProviderIdAndBarcodeIn(eq(providerId), any()))
            .thenReturn(
                Offering(
                    offeringId = UUID.randomUUID(),
                    providerId = providerId,
                    name = "Existing product",
                    price = BigDecimal.TEN,
                    stockQuantity = 1,
                    barcode = barcode
                )
            )

        val exception = assertThrows<BarcodeConflictException> {
            catalogService.createOffering(
                Offering(
                    providerId = providerId,
                    name = "Duplicate product",
                    price = BigDecimal.TEN,
                    stockQuantity = 1,
                    barcode = "0$barcode"
                )
            )
        }

        assertTrue(exception.message!!.contains("already belongs"))
        verify(offeringRepository, never()).save(any())
    }

    @Test
    fun `createOffering delivery provider requires stock`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId))
            .thenReturn(Optional.of(Provider(providerId, FulfillmentType.DELIVERY)))

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createOffering(
                Offering(
                    providerId = providerId,
                    name = "Dog Food",
                    price = BigDecimal("499.00"),
                    stockQuantity = null
                )
            )
        }
        assertTrue(exception.message!!.contains("must specify a stock quantity"))
    }

    @Test
    fun `createOffering appointment provider rejects barcode`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId))
            .thenReturn(Optional.of(Provider(providerId, FulfillmentType.APPOINTMENT)))

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createOffering(
                Offering(
                    providerId = providerId,
                    name = "Vet Checkup",
                    price = BigDecimal("600.00"),
                    durationMinutes = 30,
                    barcode = "ABC123"
                )
            )
        }
        assertTrue(exception.message!!.contains("cannot specify a barcode"))
    }

    @Test
    fun `createSlot rejects delivery offering`() {
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId))
            .thenReturn(Optional.of(Provider(providerId, FulfillmentType.DELIVERY)))
        whenever(offeringRepository.findById(offeringId)).thenReturn(
            Optional.of(
                Offering(
                    offeringId = offeringId,
                    providerId = providerId,
                    name = "Dog Toy",
                    price = BigDecimal("150.00"),
                    stockQuantity = 10
                )
            )
        )

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createSlot(
                Slot(
                    offeringId = offeringId,
                    slotStart = Instant.now(),
                    slotEnd = Instant.now().plusSeconds(1800)
                )
            )
        }
        assertTrue(exception.message!!.contains("Cannot create slots for a DELIVERY"))
    }

    @Test
    fun `createSlot accepts appointment offering`() {
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId))
            .thenReturn(Optional.of(Provider(providerId, FulfillmentType.APPOINTMENT)))
        whenever(offeringRepository.findById(offeringId)).thenReturn(
            Optional.of(
                Offering(
                    offeringId = offeringId,
                    providerId = providerId,
                    name = "Grooming",
                    price = BigDecimal("800.00"),
                    durationMinutes = 45
                )
            )
        )
        whenever(slotRepository.save(any<Slot>())).thenAnswer { it.arguments[0] as Slot }

        val created = catalogService.createSlot(
            Slot(
                offeringId = offeringId,
                slotStart = Instant.now(),
                slotEnd = Instant.now().plusSeconds(2700)
            )
        )
        assertEquals(offeringId, created.offeringId)
    }

    @Test
    fun `decrementStock uses atomic guard`() {
        val providerId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val offering = Offering(
            offeringId = offeringId,
            providerId = providerId,
            name = "Dog Food",
            price = BigDecimal("499.00"),
            stockQuantity = 5,
            barcode = "123456789012"
        )
        whenever(offeringRepository.findById(offeringId)).thenReturn(Optional.of(offering))
        whenever(offeringRepository.decrementStockIfAvailable(offeringId, providerId, 2)).thenReturn(1)

        val updated = catalogService.decrementStock(offeringId, 2)

        assertEquals(3, updated.stockQuantity)
        verify(offeringRepository).decrementStockIfAvailable(offeringId, providerId, 2)
        verify(offeringRepository, never()).save(any())
    }

    @Test
    fun `restoreStock increments tracked inventory`() {
        val providerId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val offering = Offering(
            offeringId = offeringId,
            providerId = providerId,
            name = "Returned Food",
            price = BigDecimal("299.00"),
            stockQuantity = 4,
            barcode = "123456789012"
        )
        whenever(offeringRepository.findById(offeringId)).thenReturn(Optional.of(offering))
        whenever(offeringRepository.incrementStockIfTracked(offeringId, 3)).thenReturn(1)

        assertEquals(7, catalogService.restoreStock(offeringId, 3).stockQuantity)
        verify(offeringRepository).incrementStockIfTracked(offeringId, 3)
    }

    @Test
    fun `getOfferingByBarcode cache stores only offering identity and returns fresh entity`() {
        val providerId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val barcode = "123456789012"
        val offering = Offering(
            offeringId = offeringId,
            providerId = providerId,
            name = "Test Product",
            price = BigDecimal("10.00"),
            stockQuantity = 5,
            barcode = barcode
        )
        val valueOps: ValueOperations<String, String> = mock()
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("barcodes:cache:$providerId:$barcode")).thenReturn(offeringId.toString())
        whenever(offeringRepository.findById(offeringId)).thenReturn(Optional.of(offering))

        val result = catalogService.getOfferingByBarcode(providerId, "0$barcode")

        assertSame(offering, result)
        assertEquals(5, result.stockQuantity)
        verify(offeringRepository, never()).findFirstByProviderIdAndBarcodeIn(any(), any())
    }

    @Test
    fun `getOfferingByBarcode resolves UPC and EAN aliases on cache miss`() {
        val providerId = UUID.randomUUID()
        val barcode = "123456789012"
        val offering = Offering(
            offeringId = UUID.randomUUID(),
            providerId = providerId,
            name = "Alias Product",
            price = BigDecimal("25.00"),
            stockQuantity = 2,
            barcode = barcode
        )
        val valueOps: ValueOperations<String, String> = mock()
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(offeringRepository.findFirstByProviderIdAndBarcodeIn(eq(providerId), any()))
            .thenReturn(offering)

        val result = catalogService.getOfferingByBarcode(providerId, "0$barcode")

        assertEquals(offering.offeringId, result.offeringId)
        verify(offeringRepository).findFirstByProviderIdAndBarcodeIn(eq(providerId), any())
    }

    @Test
    fun `createBill uses server price canonical barcode and records partial stock failure`() {
        val storeId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val firstProductId = UUID.randomUUID()
        val secondProductId = UUID.randomUUID()
        val first = Offering(
            offeringId = firstProductId,
            providerId = storeId,
            name = "Product 1",
            price = BigDecimal("10.00"),
            stockQuantity = 5,
            barcode = "123456789012"
        )
        val second = Offering(
            offeringId = secondProductId,
            providerId = storeId,
            name = "Product 2",
            price = BigDecimal("20.00"),
            stockQuantity = 1,
            barcode = "223456789012"
        )
        whenever(offeringRepository.findById(firstProductId)).thenReturn(Optional.of(first))
        whenever(offeringRepository.findById(secondProductId)).thenReturn(Optional.of(second))
        whenever(offeringRepository.decrementStockIfAvailable(firstProductId, storeId, 2)).thenReturn(1)
        whenever(billRepository.save(any<Bill>())).thenAnswer {
            (it.arguments[0] as Bill).also { bill -> bill.id = bill.id ?: UUID.randomUUID() }
        }
        whenever(billItemRepository.save(any<BillItem>())).thenAnswer { it.arguments[0] as BillItem }

        val response = catalogService.createBill(
            billRequest(
                storeId = storeId,
                staffId = staffId,
                items = listOf(
                    BillItemRequest(
                        productId = firstProductId,
                        barcodeScanned = "0123456789012",
                        quantity = 2,
                        unitPrice = BigDecimal("999.00"),
                        discountAmount = BigDecimal.ZERO,
                        discountType = "NONE"
                    ),
                    BillItemRequest(
                        productId = secondProductId,
                        barcodeScanned = second.barcode,
                        quantity = 2,
                        unitPrice = BigDecimal("20.00"),
                        discountAmount = BigDecimal.ZERO,
                        discountType = "NONE"
                    )
                )
            )
        )

        assertEquals(1, response.successfulItems.size)
        assertEquals(1, response.failedItems.size)
        assertEquals(BigDecimal("10.00"), response.successfulItems.single().unitPrice)
        assertEquals("123456789012", response.successfulItems.single().barcodeScanned)
        assertEquals(BigDecimal("20.00"), response.bill.subtotal)
        assertEquals(3, first.stockQuantity)
        assertEquals(1, second.stockQuantity)
    }

    @Test
    fun `createBill rejects cross-store products and rolls back empty bill`() {
        val storeId = UUID.randomUUID()
        val otherStoreId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        whenever(offeringRepository.findById(productId)).thenReturn(
            Optional.of(
                Offering(
                    offeringId = productId,
                    providerId = otherStoreId,
                    name = "Foreign Product",
                    price = BigDecimal.TEN,
                    stockQuantity = 5,
                    barcode = "ABC123"
                )
            )
        )
        whenever(billRepository.save(any<Bill>())).thenAnswer {
            (it.arguments[0] as Bill).also { bill -> bill.id = bill.id ?: UUID.randomUUID() }
        }

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createBill(
                billRequest(
                    storeId,
                    staffId,
                    listOf(
                        BillItemRequest(
                            productId = productId,
                            barcodeScanned = "ABC123",
                            quantity = 1,
                            unitPrice = BigDecimal.TEN,
                            discountAmount = BigDecimal.ZERO,
                            discountType = "NONE"
                        )
                    )
                )
            )
        }

        assertTrue(exception.message!!.contains("does not belong"))
        verify(offeringRepository, never()).decrementStockIfAvailable(any(), any(), any())
        verify(billItemRepository, never()).save(any())
    }

    private fun billRequest(
        storeId: UUID,
        staffId: UUID,
        items: List<BillItemRequest>
    ) = BillRequest(
        storeId = storeId,
        staffId = staffId,
        status = "FINALIZED",
        subtotal = BigDecimal.ZERO,
        totalDiscount = BigDecimal.ZERO,
        tax = BigDecimal.ZERO,
        grandTotal = BigDecimal.ZERO,
        idempotencyKey = "idem-${UUID.randomUUID()}",
        items = items
    )
}
