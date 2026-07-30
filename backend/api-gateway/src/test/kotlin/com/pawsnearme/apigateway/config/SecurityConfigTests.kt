package com.pawsnearme.apigateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class SecurityConfigTests {

    @Test
    fun `cors uses only explicitly configured origins`() {
        val securityConfig = SecurityConfig(
            jwkSetUri = "",
            secretKey = "test-secret",
            allowUnsignedJwt = false,
            corsAllowedOrigins = "https://app.example.com, https://admin.example.com"
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/catalog/offerings"))

        val cors = securityConfig.corsConfigurationSource().getCorsConfiguration(exchange)

        assertEquals(listOf("https://app.example.com", "https://admin.example.com"), cors?.allowedOrigins)
        assertEquals(true, cors?.allowCredentials)
        assertEquals(true, cors?.allowedMethods?.contains("PATCH"))
    }

    @Test
    fun `cors rejects wildcard origins when credentials are enabled`() {
        val securityConfig = SecurityConfig(
            jwkSetUri = "",
            secretKey = "test-secret",
            allowUnsignedJwt = false,
            corsAllowedOrigins = "*"
        )

        assertThrows(IllegalArgumentException::class.java) {
            securityConfig.corsConfigurationSource()
        }
    }
}
