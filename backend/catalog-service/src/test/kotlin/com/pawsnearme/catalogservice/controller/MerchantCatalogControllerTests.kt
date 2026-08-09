package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.catalogservice.repository.ProviderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.util.UUID

class MerchantCatalogControllerTests {
    private val offeringRepository: OfferingRepository = mock()
    private val providerRepository: ProviderRepository = mock()
    private val controller = MerchantCatalogController(offeringRepository, providerRepository)

    @Test
    fun `merchant sees only owner-scoped paginated catalog`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val offering = Offering(
            offeringId = UUID.randomUUID(),
            providerId = providerId,
            name = "Dog food",
            price = BigDecimal("450.00"),
            status = OfferingStatus.ACTIVE,
            stockQuantity = 12,
        )
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(providerId, ownerId)).thenReturn(true)
        whenever(offeringRepository.searchMerchantOfferings(eq(providerId), eq("dog"), any<Pageable>()))
            .thenAnswer { invocation ->
                val pageable = invocation.arguments[2] as Pageable
                assertEquals(40, pageable.pageSize)
                assertEquals(0, pageable.pageNumber)
                PageImpl(listOf(offering), pageable, 1)
            }

        val response = controller.listMerchantOfferings(
            providerId = providerId,
            query = " dog ",
            page = -2,
            size = 40,
            xUserId = ownerId.toString(),
            xUserRole = "MERCHANT",
        ).body!!

        assertEquals(0, response.page)
        assertEquals(40, response.size)
        assertEquals(1, response.totalElements)
        assertEquals(listOf(offering), response.content)
        verify(providerRepository).existsByProviderIdAndOwnerUserId(providerId, ownerId)
    }

    @Test
    fun `merchant catalog page size is capped at one hundred`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(providerId, ownerId)).thenReturn(true)
        whenever(offeringRepository.searchMerchantOfferings(eq(providerId), eq(""), any<Pageable>()))
            .thenAnswer { invocation ->
                val pageable = invocation.arguments[2] as Pageable
                assertEquals(100, pageable.pageSize)
                PageImpl<Offering>(emptyList(), pageable, 0)
            }

        val response = controller.listMerchantOfferings(
            providerId = providerId,
            query = "",
            page = 0,
            size = 5000,
            xUserId = ownerId.toString(),
            xUserRole = "MERCHANT",
        ).body!!

        assertEquals(100, response.size)
        assertTrue(response.content.isEmpty())
    }

    @Test
    fun `different merchant cannot search another provider catalog`() {
        val providerId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(providerId, requesterId)).thenReturn(false)

        assertThrows<CatalogAccessDeniedException> {
            controller.listMerchantOfferings(
                providerId = providerId,
                query = "",
                page = 0,
                size = 50,
                xUserId = requesterId.toString(),
                xUserRole = "MERCHANT",
            )
        }
    }

    @Test
    fun `customer role cannot access merchant catalog endpoint`() {
        assertThrows<CatalogAccessDeniedException> {
            controller.listMerchantOfferings(
                providerId = UUID.randomUUID(),
                query = "",
                page = 0,
                size = 50,
                xUserId = UUID.randomUUID().toString(),
                xUserRole = "CUSTOMER",
            )
        }
    }
}
