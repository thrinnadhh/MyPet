package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.*
import com.pawsnearme.catalogservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CatalogServiceTests {

    private val offeringRepository: OfferingRepository = mock()
    private val slotRepository: SlotRepository = mock()
    private val providerRepository: ProviderRepository = mock()

    private val catalogService = CatalogService(offeringRepository, slotRepository, providerRepository)

    @Test
    fun `createOffering - delivery provider - success`() {
        val providerId = UUID.randomUUID()
        val provider = Provider(providerId, "DELIVERY")
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
        val provider = Provider(providerId, "DELIVERY")
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
        val provider = Provider(providerId, "DELIVERY")
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
        val provider = Provider(providerId, "APPOINTMENT")
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
        val provider = Provider(providerId, "APPOINTMENT")
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
        val provider = Provider(providerId, "APPOINTMENT")
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
        val offering = Offering(
            offeringId = offeringId,
            providerId = UUID.randomUUID(),
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
        val offering = Offering(
            offeringId = offeringId,
            providerId = UUID.randomUUID(),
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
}
