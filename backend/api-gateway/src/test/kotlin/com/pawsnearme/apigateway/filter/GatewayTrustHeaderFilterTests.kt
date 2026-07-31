package com.pawsnearme.apigateway.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

class GatewayTrustHeaderFilterTests {

    @Test
    fun `replaces a forged gateway secret with the configured trusted secret`() {
        val filter = GatewayTrustHeaderFilter(true, "0123456789abcdef")
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/catalog/offerings")
                .header(GatewayTrustHeaderFilter.SECRET_HEADER, "forged-client-value")
        )
        val captured = AtomicReference<ServerWebExchange>()
        val chain = GatewayFilterChain {
            captured.set(it)
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertEquals(
            "0123456789abcdef",
            captured.get().request.headers.getFirst(GatewayTrustHeaderFilter.SECRET_HEADER)
        )
    }

    @Test
    fun `does not inject a trust secret when gateway trust is disabled`() {
        val filter = GatewayTrustHeaderFilter(false, "")
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health/readiness")
        )
        val captured = AtomicReference<ServerWebExchange>()
        val chain = GatewayFilterChain {
            captured.set(it)
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertEquals(
            null,
            captured.get().request.headers.getFirst(GatewayTrustHeaderFilter.SECRET_HEADER)
        )
    }

    @Test
    fun `fails closed when trust is enabled without a secret`() {
        assertThrows(IllegalArgumentException::class.java) {
            GatewayTrustHeaderFilter(true, "")
        }
    }

    @Test
    fun `rejects the development secret when trust is enabled`() {
        assertThrows(IllegalArgumentException::class.java) {
            GatewayTrustHeaderFilter(true, "dev-secret-change-in-production")
        }
    }

    @Test
    fun `runs after normal route sanitizers and before downstream routing`() {
        val filter = GatewayTrustHeaderFilter(true, "0123456789abcdef")

        assertEquals(10_000, filter.order)
    }
}
