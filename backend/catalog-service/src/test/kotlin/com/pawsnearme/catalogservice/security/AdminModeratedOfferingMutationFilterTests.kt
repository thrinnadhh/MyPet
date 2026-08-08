package com.pawsnearme.catalogservice.security

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.repository.OfferingRepository
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class AdminModeratedOfferingMutationFilterTests {
    private val offeringRepository: OfferingRepository = mock()
    private val filter = AdminModeratedOfferingMutationFilter(offeringRepository)
    private val chain: FilterChain = mock()

    @Test
    fun `merchant cannot update admin moderated listing`() {
        val id = UUID.randomUUID()
        whenever(offeringRepository.findById(id)).thenReturn(Optional.of(offering(id, true)))
        val request = MockHttpServletRequest("PUT", "/api/v1/catalog/offerings/$id").apply {
            addHeader("X-User-Role", "MERCHANT")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertEquals(409, response.status)
        verify(chain, never()).doFilter(request, response)
    }

    @Test
    fun `admin moderation endpoint remains able to operate on moderated listing`() {
        val id = UUID.randomUUID()
        whenever(offeringRepository.findById(id)).thenReturn(Optional.of(offering(id, true)))
        val request = MockHttpServletRequest("PUT", "/api/v1/catalog/offerings/$id").apply {
            addHeader("X-User-Role", "ADMIN")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
    }

    private fun offering(id: UUID, moderated: Boolean) = Offering(
        offeringId = id,
        providerId = UUID.randomUUID(),
        name = "Product",
        price = BigDecimal("199.00"),
        status = OfferingStatus.INACTIVE,
        adminDisabled = moderated
    )
}
