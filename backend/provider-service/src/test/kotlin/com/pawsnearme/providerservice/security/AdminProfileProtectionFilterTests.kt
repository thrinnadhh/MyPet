package com.pawsnearme.providerservice.security

import com.pawsnearme.providerservice.model.Profile
import com.pawsnearme.providerservice.model.UserRole
import com.pawsnearme.providerservice.repository.ProfileRepository
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.Optional
import java.util.UUID

class AdminProfileProtectionFilterTests {
    private val profileRepository: ProfileRepository = mock()
    private val filter = AdminProfileProtectionFilter(profileRepository)
    private val chain: FilterChain = mock()

    @Test
    fun `generic revoke endpoint cannot suspend admin identity`() {
        val targetId = UUID.randomUUID()
        whenever(profileRepository.findById(targetId)).thenReturn(
            Optional.of(
                Profile(
                    userId = targetId,
                    role = UserRole.ADMIN,
                    fullName = "Operations Admin",
                    phoneNumber = "+919900000010"
                )
            )
        )
        val request = MockHttpServletRequest("POST", "/api/v1/profiles/$targetId/revoke")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertEquals(403, response.status)
        assertTrue(response.contentAsString.contains("ADMIN identities cannot be suspended"))
        verify(chain, never()).doFilter(request, response)
    }

    @Test
    fun `generic revoke endpoint still permits non admin identity`() {
        val targetId = UUID.randomUUID()
        whenever(profileRepository.findById(targetId)).thenReturn(
            Optional.of(
                Profile(
                    userId = targetId,
                    role = UserRole.CUSTOMER,
                    fullName = "Customer",
                    phoneNumber = "+919900000011"
                )
            )
        )
        val request = MockHttpServletRequest("POST", "/api/v1/profiles/$targetId/revoke")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        verify(chain).doFilter(request, response)
    }
}
