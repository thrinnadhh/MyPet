package com.pawsnearme.apigateway.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class AuthenticationHeaderFilterTests {

    private val filter = AuthenticationHeaderFilter()

    @Test
    fun `removes spoofed identity headers when request is anonymous`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "ADMIN")
                .header("X-Admin-Api-Key", "legacy-key")
        )
        val capturedExchange = AtomicReference<org.springframework.web.server.ServerWebExchange>()
        val chain = GatewayFilterChain {
            capturedExchange.set(it)
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        val headers = capturedExchange.get().request.headers
        assertNull(headers.getFirst("X-User-Id"))
        assertNull(headers.getFirst("X-User-Role"))
        assertNull(headers.getFirst("X-User-Email"))
        assertNull(headers.getFirst("X-User-Full-Name"))
        assertNull(headers.getFirst("X-User-Phone"))
        assertNull(headers.getFirst("X-Admin-Api-Key"))
    }

    @Test
    fun `replaces spoofed headers with jwt subject and normalized role`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "spoof@example.com")
                .header("X-User-Full-Name", "Spoof Name")
                .header("X-User-Phone", "+910000000000")
                .header("X-Admin-Api-Key", "legacy-key")
        )
        val capturedExchange = AtomicReference<org.springframework.web.server.ServerWebExchange>()
        val chain = GatewayFilterChain {
            capturedExchange.set(it)
            Mono.empty()
        }
        val jwt = Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            mapOf("alg" to "none"),
            mapOf(
                "sub" to "real-user",
                "role" to "merchant",
                "email" to "real@example.com",
                "user_metadata" to mapOf(
                    "full_name" to "Real User",
                    "phone" to "+919999111111"
                )
            )
        )
        val authentication = TestingAuthenticationToken(jwt, null)
        val securityContext = SecurityContextImpl(authentication)

        filter.filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
            .block()

        val headers = capturedExchange.get().request.headers
        assertEquals("real-user", headers.getFirst("X-User-Id"))
        assertEquals("MERCHANT", headers.getFirst("X-User-Role"))
        assertEquals("real@example.com", headers.getFirst("X-User-Email"))
        assertEquals("Real User", headers.getFirst("X-User-Full-Name"))
        assertEquals("+919999111111", headers.getFirst("X-User-Phone"))
        assertNull(headers.getFirst("X-Admin-Api-Key"))
    }

    @Test
    fun `uses app metadata role before Supabase platform role`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/providers/pending")
        )
        val capturedExchange = AtomicReference<org.springframework.web.server.ServerWebExchange>()
        val chain = GatewayFilterChain {
            capturedExchange.set(it)
            Mono.empty()
        }
        val jwt = Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            mapOf("alg" to "none"),
            mapOf(
                "sub" to "admin-user",
                "role" to "authenticated",
                "app_metadata" to mapOf("role" to "ADMIN")
            )
        )
        val authentication = TestingAuthenticationToken(jwt, null)
        val securityContext = SecurityContextImpl(authentication)

        filter.filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
            .block()

        val headers = capturedExchange.get().request.headers
        assertEquals("admin-user", headers.getFirst("X-User-Id"))
        assertEquals("ADMIN", headers.getFirst("X-User-Role"))
    }

    @Test
    fun `normalizes legacy provider role to merchant`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/providers")
        )
        val capturedExchange = AtomicReference<org.springframework.web.server.ServerWebExchange>()
        val chain = GatewayFilterChain {
            capturedExchange.set(it)
            Mono.empty()
        }
        val jwt = Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            mapOf("alg" to "none"),
            mapOf(
                "sub" to "provider-user",
                "role" to "authenticated",
                "app_metadata" to mapOf("role" to "PROVIDER")
            )
        )
        val authentication = TestingAuthenticationToken(jwt, null)
        val securityContext = SecurityContextImpl(authentication)

        filter.filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
            .block()

        val headers = capturedExchange.get().request.headers
        assertEquals("provider-user", headers.getFirst("X-User-Id"))
        assertEquals("MERCHANT", headers.getFirst("X-User-Role"))
    }
}
