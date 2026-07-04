package com.pawsnearme.apigateway.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

class RoleGuardGatewayFilterFactoryTests {

    private val factory = RoleGuardGatewayFilterFactory()

    @Test
    fun `allows requests with configured role ignoring case`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").header("X-User-Role", "merchant")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("MERCHANT,ADMIN"))

        filter.filter(exchange, chain).block()

        assertTrue(wasCalled.get())
    }

    @Test
    fun `rejects requests without an allowed role`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").header("X-User-Role", "CUSTOMER")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("MERCHANT,ADMIN"))

        filter.filter(exchange, chain).block()

        assertEquals(false, wasCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `rejects CUSTOMER role from appointment status updates`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.put("/api/v1/appointments/123e4567-e89b-12d3-a456-426614174000/status")
                .header("X-User-Role", "CUSTOMER")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("MERCHANT,ADMIN"))

        filter.filter(exchange, chain).block()

        assertEquals(false, wasCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `rejects MERCHANT role from banner writes`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/content/banners")
                .header("X-User-Role", "MERCHANT")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("ADMIN"))

        filter.filter(exchange, chain).block()

        assertEquals(false, wasCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `rejects CUSTOMER role from banner bid submission`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/content/banners/bids")
                .header("X-User-Role", "CUSTOMER")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("MERCHANT,ADMIN"))

        filter.filter(exchange, chain).block()

        assertEquals(false, wasCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `allows MERCHANT role for banner bid submission`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/content/banners/bids")
                .header("X-User-Role", "MERCHANT")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("MERCHANT,ADMIN"))

        filter.filter(exchange, chain).block()

        assertTrue(wasCalled.get())
    }

    @Test
    fun `rejects MERCHANT role from guide writer grant`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/content/guides/writers")
                .header("X-User-Role", "MERCHANT")
        )
        val wasCalled = AtomicBoolean(false)
        val chain = GatewayFilterChain {
            wasCalled.set(true)
            Mono.empty()
        }
        val filter = factory.apply(RoleGuardGatewayFilterFactory.Config("ADMIN"))

        filter.filter(exchange, chain).block()

        assertEquals(false, wasCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }
}
