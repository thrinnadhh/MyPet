package com.pawsnearme.application.edge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "mypet.edge.enabled=true",
        "mypet.edge.jwt.allow-unsigned=true",
        "mypet.edge.rate-limit.enabled=false",
        "mypet.edge.idempotency.enabled=false",
        "mypet.edge.cors.allowed-origins=https://app.example.com,http://localhost:8081"
    ]
)
@ActiveProfiles("test")
class EdgeSecurityIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockBean
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `public routes remove spoofed identity and create request id`() {
        val headers = HttpHeaders().apply {
            set("X-User-Id", "attacker")
            set("X-User-Role", "ADMIN")
            set("X-Internal-Gateway-Secret", "forged")
            set(EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER, "invalid request id")
        }

        val response = restTemplate.exchange(
            "/api/v1/discovery/test",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body?.get("userId"))
        assertNull(response.body?.get("role"))
        assertNull(response.body?.get("internalSecret"))
        val requestId = response.headers.getFirst(EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER)
        assertNotNull(requestId)
        assertFalse(requestId!!.contains(' '))
        assertEquals(requestId, response.body?.get("requestId"))
    }

    @Test
    fun `protected routes require a bearer token`() {
        val response = restTemplate.getForEntity("/api/v1/orders/test", Map::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("Authentication required", response.body?.get("error"))
    }

    @Test
    fun `validated jwt replaces spoofed identity headers`() {
        val headers = authorizedHeaders(role = "PROVIDER").apply {
            set("X-User-Id", "attacker")
            set("X-User-Role", "ADMIN")
            set("X-User-Email", "attacker@example.com")
            set("X-Internal-Secret", "forged")
            set(EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER, "request-123")
        }

        val response = restTemplate.exchange(
            "/api/v1/orders/test",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("user-123", response.body?.get("userId"))
        assertEquals("MERCHANT", response.body?.get("role"))
        assertEquals("user@example.com", response.body?.get("email"))
        assertEquals("Test User", response.body?.get("fullName"))
        assertEquals("9999999999", response.body?.get("phone"))
        assertNull(response.body?.get("internalSecret"))
        assertEquals("request-123", response.body?.get("requestId"))
        assertEquals(
            "request-123",
            response.headers.getFirst(EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER)
        )
    }

    @Test
    fun `gateway role guard behavior is preserved`() {
        val customerResponse = restTemplate.exchange(
            "/api/v1/providers",
            HttpMethod.POST,
            HttpEntity("{}", authorizedHeaders("CUSTOMER")),
            Map::class.java
        )
        assertEquals(HttpStatus.FORBIDDEN, customerResponse.statusCode)
        assertEquals("Access denied: insufficient role", customerResponse.body?.get("error"))

        val merchantResponse = restTemplate.exchange(
            "/api/v1/providers",
            HttpMethod.POST,
            HttpEntity("{}", authorizedHeaders("MERCHANT")),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, merchantResponse.statusCode)
        assertEquals("MERCHANT", merchantResponse.body?.get("role"))
    }

    @Test
    fun `cors permits configured origins and rejects other origins`() {
        val allowedHeaders = HttpHeaders().apply {
            origin = "https://app.example.com"
            accessControlRequestMethod = HttpMethod.GET
            accessControlRequestHeaders = listOf("Authorization", "Idempotency-Key", "X-Request-Id")
        }
        val allowed = restTemplate.exchange(
            "/api/v1/orders/test",
            HttpMethod.OPTIONS,
            HttpEntity<Void>(allowedHeaders),
            String::class.java
        )
        assertEquals(HttpStatus.OK, allowed.statusCode)
        assertEquals("https://app.example.com", allowed.headers.accessControlAllowOrigin)
        assertEquals(true, allowed.headers.accessControlAllowCredentials)

        val deniedHeaders = HttpHeaders().apply {
            origin = "https://attacker.example"
            accessControlRequestMethod = HttpMethod.GET
        }
        val denied = restTemplate.exchange(
            "/api/v1/orders/test",
            HttpMethod.OPTIONS,
            HttpEntity<Void>(deniedHeaders),
            String::class.java
        )
        assertEquals(HttpStatus.FORBIDDEN, denied.statusCode)
        assertNull(denied.headers.accessControlAllowOrigin)
    }

    private fun authorizedHeaders(role: String): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(unsignedJwt(role = role))
        contentType = MediaType.APPLICATION_JSON
    }
}
