package com.pawsnearme.application.edge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
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
        "mypet.edge.idempotency.enabled=true",
        "mypet.edge.idempotency.ttl-seconds=60",
        "mypet.edge.idempotency.max-entries=100",
        "mypet.edge.idempotency.max-body-bytes=4096"
    ]
)
@ActiveProfiles("test")
class EdgeIdempotencyIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var store: InMemoryIdempotencyStore

    @BeforeEach
    fun reset() {
        store.clear()
        EdgeSecurityTestController.idempotentExecutions.set(0)
    }

    @Test
    fun `same key and payload replays the original response`() {
        val request = request("order-create-0001", "{\"value\":1}")

        val first = exchange(request)
        val second = exchange(request)

        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.body, second.body)
        assertEquals(1, first.body?.get("execution"))
        assertEquals("true", second.headers.getFirst(IdempotencyFilter.REPLAYED_HEADER))
        assertEquals(1, EdgeSecurityTestController.idempotentExecutions.get())
    }

    @Test
    fun `same key with a different payload is rejected`() {
        val first = exchange(request("order-create-0002", "{\"value\":1}"))
        val conflict = exchange(request("order-create-0002", "{\"value\":2}"))

        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(HttpStatus.CONFLICT, conflict.statusCode)
        assertEquals(
            "Idempotency-Key was already used for a different request",
            conflict.body?.get("error")
        )
        assertEquals(1, EdgeSecurityTestController.idempotentExecutions.get())
    }

    @Test
    fun `invalid idempotency key is rejected before controller execution`() {
        val response = exchange(request("bad", "{}"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Invalid Idempotency-Key", response.body?.get("error"))
        assertEquals(0, EdgeSecurityTestController.idempotentExecutions.get())
    }

    private fun request(key: String, body: String): HttpEntity<String> {
        val headers = HttpHeaders().apply {
            setBearerAuth(unsignedJwt())
            set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
            contentType = MediaType.APPLICATION_JSON
        }
        return HttpEntity(body, headers)
    }

    private fun exchange(request: HttpEntity<String>) = restTemplate.exchange(
        "/api/v1/orders/idempotency-test",
        HttpMethod.POST,
        request,
        Map::class.java
    )
}
