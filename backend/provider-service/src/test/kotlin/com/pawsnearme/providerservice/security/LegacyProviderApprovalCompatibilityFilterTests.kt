package com.pawsnearme.providerservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.service.ProviderAdminApprovalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.math.BigDecimal
import java.util.UUID

class LegacyProviderApprovalCompatibilityFilterTests {
    private val approvalService: ProviderAdminApprovalService = mock()
    private val filter = LegacyProviderApprovalCompatibilityFilter(approvalService, ObjectMapper())
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `merchant cannot use compatibility approval route`() {
        val providerId = UUID.randomUUID()
        val request = MockHttpServletRequest("POST", "/api/v1/providers/$providerId/approve").apply {
            addHeader("X-User-Id", UUID.randomUUID().toString())
            addHeader("X-User-Role", "MERCHANT")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(403, response.status)
        verify(approvalService, never()).approve(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `admin compatibility approval delegates to locked audited service`() {
        val providerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        whenever(approvalService.approve(providerId, actorId)).thenReturn(provider(providerId))
        val request = MockHttpServletRequest("POST", "/api/v1/providers/$providerId/approve").apply {
            addHeader("X-User-Id", actorId.toString())
            addHeader("X-User-Role", "ADMIN")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"status\":\"ACTIVE\""))
        verify(approvalService).approve(providerId, actorId)
    }

    private fun provider(providerId: UUID) = Provider(
        providerId = providerId,
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "M8 Pet Store",
        addressLine = "Verification Road",
        city = "Tirupati",
        pincode = "517501",
        geoLocation = geometryFactory.createPoint(Coordinate(79.4192, 13.6288)),
        status = ProviderStatus.ACTIVE,
        commissionPct = BigDecimal("15.00"),
    )
}
