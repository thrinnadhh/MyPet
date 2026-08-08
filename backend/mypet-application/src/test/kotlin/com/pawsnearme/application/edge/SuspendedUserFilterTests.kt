package com.pawsnearme.application.edge

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class SuspendedUserFilterTests {
    private val redisTemplate: StringRedisTemplate = mock(StringRedisTemplate::class.java)
    private val filter = SuspendedUserFilter(redisTemplate)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `already issued jwt is rejected after admin suspension`() {
        val userId = "0b940ca7-1877-4b0a-aace-b1e46338d65a"
        authenticate(userId)
        `when`(redisTemplate.hasKey("suspended_user:$userId")).thenReturn(true)
        val request = MockHttpServletRequest("POST", "/api/v1/orders")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(403)
        assertThat(response.contentAsString).contains("User access has been revoked")
        verify(chain, never()).doFilter(request, response)
    }

    @Test
    fun `non suspended authenticated user continues through edge`() {
        val userId = "3a8826c6-d5a0-44ff-a983-49879799f2ca"
        authenticate(userId)
        `when`(redisTemplate.hasKey("suspended_user:$userId")).thenReturn(false)
        val request = MockHttpServletRequest("GET", "/api/v1/orders")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        verify(chain).doFilter(request, response)
    }

    private fun authenticate(subject: String) {
        val now = Instant.now()
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("app_metadata", mapOf("role" to "CUSTOMER"))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }
}
