package com.pawsnearme.application.edge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "mypet.edge.enabled=true",
        "mypet.edge.jwt.allow-unsigned=true",
        "mypet.edge.rate-limit.enabled=true",
        "mypet.edge.rate-limit.replenish-rate=1",
        "mypet.edge.rate-limit.burst-capacity=2",
        "mypet.edge.rate-limit.trust-forwarded-for=true",
        "mypet.edge.idempotency.enabled=false"
    ]
)
@ActiveProfiles("test")
class EdgeRateLimitIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `client receives 429 after burst capacity is exhausted`() {
        val headers = HttpHeaders().apply {
            set("X-Forwarded-For", "203.0.113.77")
        }
        val request = HttpEntity<Void>(headers)

        val first = restTemplate.exchange(
            "/api/v1/discovery/test",
            HttpMethod.GET,
            request,
            Map::class.java
        )
        val second = restTemplate.exchange(
            "/api/v1/discovery/test",
            HttpMethod.GET,
            request,
            Map::class.java
        )
        val third = restTemplate.exchange(
            "/api/v1/discovery/test",
            HttpMethod.GET,
            request,
            Map::class.java
        )

        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, third.statusCode)
        assertEquals("0", third.headers.getFirst("X-RateLimit-Remaining"))
        assertEquals("1", third.headers.getFirst("Retry-After"))
        assertEquals("Too many requests", third.body?.get("error"))
    }
}
