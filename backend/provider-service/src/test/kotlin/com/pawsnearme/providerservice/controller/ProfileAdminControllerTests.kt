package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Profile
import com.pawsnearme.providerservice.model.UserRole
import com.pawsnearme.providerservice.repository.ProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class ProfileAdminControllerTests {
    private val profileRepository: ProfileRepository = mock()
    private val controller = ProfileAdminController(profileRepository)

    @Test
    fun `admin profile listing is paginated and bounded`() {
        val adminId = UUID.randomUUID()
        val customer = Profile(
            userId = UUID.randomUUID(),
            role = UserRole.CUSTOMER,
            fullName = "Customer One",
            phoneNumber = "+919900000001"
        )
        whenever(profileRepository.findAll(any<Pageable>())).thenAnswer { invocation ->
            val pageable = invocation.getArgument<Pageable>(0)
            PageImpl(listOf(customer), pageable, 51L)
        }

        val response = controller.listProfiles(
            page = 1,
            size = 25,
            userId = adminId.toString(),
            role = "ADMIN"
        )

        val body = requireNotNull(response.body)
        assertEquals(1, body.page)
        assertEquals(25, body.size)
        assertEquals(51L, body.totalElements)
        assertEquals(3, body.totalPages)
        assertEquals(1, body.content.size)
        assertEquals(customer.userId, body.content.single().userId)
    }

    @Test
    fun `non admin cannot enumerate profiles`() {
        assertThrows<ResponseStatusException> {
            controller.listProfiles(
                page = 0,
                size = 25,
                userId = UUID.randomUUID().toString(),
                role = "MERCHANT"
            )
        }
        verify(profileRepository, never()).findAll(any<Pageable>())
    }

    @Test
    fun `admin profile listing rejects unbounded page size`() {
        assertThrows<ResponseStatusException> {
            controller.listProfiles(
                page = 0,
                size = 1000,
                userId = UUID.randomUUID().toString(),
                role = "ADMIN"
            )
        }
        verify(profileRepository, never()).findAll(any<Pageable>())
    }
}
