package com.pawsnearme.discoveryservice.service

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.repository.CustomerFavouriteRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.Optional
import java.util.UUID

class CustomerFavouriteServiceTests {

    private lateinit var favouriteRepository: CustomerFavouriteRepository
    private lateinit var favouriteService: CustomerFavouriteService

    @BeforeEach
    fun setUp() {
        favouriteRepository = mock()
        favouriteService = CustomerFavouriteService(favouriteRepository)
    }

    @Test
    fun `getCustomerFavourites - returns user favourites`() {
        val customerId = UUID.randomUUID()
        val fav = CustomerFavourite(
            id = UUID.randomUUID(),
            customerId = customerId,
            targetType = "PRODUCT",
            targetId = "p1"
        )
        whenever(favouriteRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(listOf(fav))

        val result = favouriteService.getCustomerFavourites(customerId)

        assertEquals(1, result.size)
        assertEquals("PRODUCT", result[0].targetType)
        assertEquals("p1", result[0].targetId)
    }

    @Test
    fun `addFavourite - creates new favourite if not exists`() {
        val customerId = UUID.randomUUID()
        val req = AddFavouriteRequest(targetType = "shop", targetId = "petcare-pharmacy")

        whenever(favouriteRepository.findByCustomerIdAndTargetTypeAndTargetId(customerId, "SHOP", "petcare-pharmacy"))
            .thenReturn(Optional.empty())
        whenever(favouriteRepository.save(any<CustomerFavourite>())).thenAnswer { it.getArgument(0) }

        val created = favouriteService.addFavourite(customerId, req)

        assertEquals("SHOP", created.targetType)
        assertEquals("petcare-pharmacy", created.targetId)
        assertEquals(customerId, created.customerId)
    }
}
