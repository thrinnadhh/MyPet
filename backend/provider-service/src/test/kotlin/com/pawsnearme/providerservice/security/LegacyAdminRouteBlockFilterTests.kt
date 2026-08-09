package com.pawsnearme.providerservice.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.util.UUID

class LegacyAdminRouteBlockFilterTests {
    private val filter = LegacyAdminRouteBlockFilter()

    @Test
    fun `legacy unbounded and unaudited admin routes are blocked`() {
        val id = UUID.randomUUID()
        assertTrue(blocked("GET", "/api/v1/providers/pending"))
        assertTrue(blocked("POST", "/api/v1/providers/$id/approve"))
        assertTrue(blocked("GET", "/api/v1/profiles"))
        assertTrue(blocked("POST", "/api/v1/profiles/$id/revoke"))
        assertTrue(blocked("POST", "/api/v1/profiles/$id/restore"))
    }

    @Test
    fun `new bounded actor aware admin routes remain available`() {
        val id = UUID.randomUUID()
        assertFalse(blocked("GET", "/api/v1/providers/admin"))
        assertFalse(blocked("POST", "/api/v1/providers/admin/$id/approve"))
        assertFalse(blocked("GET", "/api/v1/profiles/admin"))
        assertFalse(blocked("POST", "/api/v1/profiles/admin/$id/revoke"))
        assertFalse(blocked("POST", "/api/v1/profiles/admin/$id/restore"))
    }

    private fun blocked(method: String, path: String): Boolean =
        filter.isBlocked(MockHttpServletRequest(method, path))
}
