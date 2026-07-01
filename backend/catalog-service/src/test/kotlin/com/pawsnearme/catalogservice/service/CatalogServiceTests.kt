package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.repository.*
import com.pawsnearme.catalogservice.dto.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    private val stringRedisTemplate: org.springframework.data.redis.core.StringRedisTemplate = mock()

    private val catalogService = CatalogService(
        offeringRepository,
        slotRepository,
        providerRepository,
        billRepository,
        billItemRepository,
        stringRedisTemplate
    )


    @Test
    fun `createOffering - delivery provider - success`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.DELIVERY)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            providerId = providerId,
            name = "Dog Food",
            price = BigDecimal("499.00"),
            stockQuantity = 50,
            durationMinutes = null
        )
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.arguments[0] as Offering }

        val created = catalogService.createOffering(offering)
        assertNotNull(created)
        assertEquals(50, created.stockQuantity)
        assertNull(created.durationMinutes)
    }

    @Test
    fun `createOffering - delivery provider - missing stock - fails`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.DELIVERY)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            providerId = providerId,
            name = "Dog Food",
            price = BigDecimal("499.00"),
            stockQuantity = null, // missing stock
            durationMinutes = null
        )

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createOffering(offering)
        }
        assertTrue(exception.message!!.contains("must specify a stock quantity"))
    }

    @Test
    fun `createOffering - delivery provider - has duration - fails`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.DELIVERY)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            providerId = providerId,
            name = "Dog Food",
            price = BigDecimal("499.00"),
            stockQuantity = 10,
            durationMinutes = 30 // invalid for delivery
        )

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createOffering(offering)
        }
        assertTrue(exception.message!!.contains("cannot specify a duration"))
    }

    @Test
    fun `createOffering - appointment provider - success`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.APPOINTMENT)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            providerId = providerId,
            name = "Vet Checkup",
            price = BigDecimal("600.00"),
            stockQuantity = null,
            durationMinutes = 30
        )
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.arguments[0] as Offering }

        val created = catalogService.createOffering(offering)
        assertNotNull(created)
        assertEquals(30, created.durationMinutes)
        assertNull(created.stockQuantity)
    }

    @Test
    fun `createOffering - appointment provider - missing duration - fails`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.APPOINTMENT)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            providerId = providerId,
            name = "Vet Checkup",
            price = BigDecimal("600.00"),
            stockQuantity = null,
            durationMinutes = null // missing duration
        )

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createOffering(offering)
        }
        assertTrue(exception.message!!.contains("must specify a duration"))
    }

    @Test
    fun `createOffering - appointment provider - has stock - fails`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.APPOINTMENT)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            providerId = providerId,
            name = "Vet Checkup",
            price = BigDecimal("600.00"),
            stockQuantity = 5, // invalid for appointment
            durationMinutes = 30
        )

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createOffering(offering)
        }
        assertTrue(exception.message!!.contains("cannot specify a stock quantity"))
    }

    @Test
    fun `createSlot - delivery offering - fails`() {
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.DELIVERY)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            offeringId = offeringId,
            providerId = providerId,
            name = "Dog Toy",
            price = BigDecimal("150.00"),
            stockQuantity = 10,
            durationMinutes = null // DELIVERY type
        )
        whenever(offeringRepository.findById(offeringId)).thenReturn(Optional.of(offering))

        val slot = Slot(
            offeringId = offeringId,
            slotStart = Instant.now(),
            slotEnd = Instant.now().plusSeconds(1800)
        )

        val exception = assertThrows<IllegalArgumentException> {
            catalogService.createSlot(slot)
        }
        assertTrue(exception.message!!.contains("Cannot create slots for a DELIVERY"))
    }

    @Test
    fun `createSlot - appointment offering - success`() {
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, FulfillmentType.APPOINTMENT)
        whenever(providerRepository.findById(providerId)).thenReturn(Optional.of(provider))

        val offering = Offering(
            offeringId = offeringId,
            providerId = providerId,
            name = "Grooming Session",
            price = BigDecimal("800.00"),
            stockQuantity = null,
            durationMinutes = 45 // APPOINTMENT type
        )
        whenever(offeringRepository.findById(offeringId)).thenReturn(Optional.of(offering))

        val slot = Slot(
            offeringId = offeringId,
            slotStart = Instant.now(),
            slotEnd = Instant.now().plusSeconds(2700)
        )
        whenever(slotRepository.save(any<Slot>())).thenAnswer { it.arguments[0] as Slot }

        val created = catalogService.createSlot(slot)
        assertNotNull(created)
        assertEquals(offeringId, created.offeringId)
    }

    @Test
    fun `getOfferingByBarcode - cache hit`() {
        val providerId = UUID.randomUUID()
        val barcode = "1234567890"
        val offering = Offering(
            offeringId = UUID.randomUUID(),
            providerId = providerId,
            name = "Test Product",
            price = BigDecimal("10.00"),
            stockQuantity = 5,
            barcode = barcode
        )

        // Mock Redis geo/value template ops
        val valueOps: org.springframework.data.redis.core.ValueOperations<String, String> = mock()
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOps)
        
        // Return cached JSON representation of offering
        val json = """{"offeringId":"${offering.offeringId}","providerId":"$providerId","name":"Test Product","price":10.00,"stockQuantity":5,"barcode":"$barcode"}"""
        whenever(valueOps.get("barcodes:cache:$providerId:$barcode")).thenReturn(json)

        val result = catalogService.getOfferingByBarcode(providerId, barcode)
        assertNotNull(result)
        assertEquals(offering.offeringId, result.offeringId)
        assertEquals("Test Product", result.name)
    }

    @Test
    fun `createBill - success and out of stock failed items`() {
        val storeId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val key = "idem-key-1"
        
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        
        val off1 = Offering(offeringId = p1, providerId = storeId, name = "Product 1", price = BigDecimal("10.0"), stockQuantity = 5)
        val off2 = Offering(offeringId = p2, providerId = storeId, name = "Product 2", price = BigDecimal("20.0"), stockQuantity = 1) // low stock
        
        whenever(offeringRepository.findById(p1)).thenReturn(Optional.of(off1))
        whenever(offeringRepository.findById(p2)).thenReturn(Optional.of(off2))
        whenever(offeringRepository.decrementStockIfAvailable(p1, storeId, 2)).thenReturn(1)
        whenever(offeringRepository.decrementStockIfAvailable(p2, storeId, 2)).thenReturn(0)
        
        whenever(billRepository.save(any<Bill>())).thenAnswer {
            val b = it.arguments[0] as Bill
            if (b.id == null) {
                b.id = UUID.randomUUID()
            }
            b
        }
        whenever(billItemRepository.save(any<BillItem>())).thenAnswer { it.arguments[0] as BillItem }

        
        val request = BillRequest(
            storeId = storeId,
            staffId = staffId,
            status = "FINALIZED",
            subtotal = BigDecimal("40.0"),
            totalDiscount = BigDecimal("0.0"),
            tax = BigDecimal("0.0"),
            grandTotal = BigDecimal("0.0"),
            idempotencyKey = key,
            items = listOf(
                BillItemRequest(productId = p1, barcodeScanned = "b1", quantity = 2, unitPrice = BigDecimal("10.0"), discountAmount = BigDecimal("0.0"), discountType = "NONE"),
                BillItemRequest(productId = p2, barcodeScanned = "b2", quantity = 2, unitPrice = BigDecimal("20.0"), discountAmount = BigDecimal("0.0"), discountType = "NONE") // out of stock request!
            )
        )
        
        val response = catalogService.createBill(request)
        assertNotNull(response)
        assertEquals(1, response.successfulItems.size)
        assertEquals(1, response.failedItems.size)
        assertEquals("Out of stock", response.failedItems[0].reason)
        assertEquals(p2, response.failedItems[0].productId)
        
        // Assert stock decremented only for successful items
        assertEquals(3, off1.stockQuantity) // 5 - 2 = 3
        assertEquals(1, off2.stockQuantity) // unchanged
    }

    @Test
    fun `createBill - atomic stock guard prevents oversell`() {
        val storeId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val offering = Offering(
            offeringId = productId,
            providerId = storeId,
            name = "Low Stock Food",
            price = BigDecimal("100.00"),
            stockQuantity = 1
        )

        whenever(billRepository.save(any<Bill>())).thenAnswer {
            val bill = it.arguments[0] as Bill
            bill.also { saved -> saved.id = saved.id ?: UUID.randomUUID() }
        }
        whenever(offeringRepository.findById(productId)).thenReturn(Optional.of(offering))
        whenever(offeringRepository.decrementStockIfAvailable(productId, storeId, 1)).thenReturn(0)

        val response = catalogService.createBill(
            BillRequest(
                storeId = storeId,
                staffId = staffId,
                status = "FINALIZED",
                subtotal = BigDecimal("100.00"),
                totalDiscount = BigDecimal.ZERO,
                tax = BigDecimal.ZERO,
                grandTotal = BigDecimal("100.00"),
                idempotencyKey = "idem-oversell",
                items = listOf(
                    BillItemRequest(
                        productId = productId,
                        barcodeScanned = "123",
                        quantity = 1,
                        unitPrice = BigDecimal("100.00"),
                        discountAmount = BigDecimal.ZERO,
                        discountType = "NONE"
                    )
                )
            )
        )

        assertEquals(0, response.successfulItems.size)
        assertEquals(1, response.failedItems.size)
        assertEquals("Out of stock", response.failedItems[0].reason)
        verify(billItemRepository, never()).save(any())
    }
}
