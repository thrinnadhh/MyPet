package com.pawsnearme.orderservice.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.util.UUID
import org.springframework.http.HttpMethod

class RemoteCatalogModuleApiTests {
    @Test
    fun `offering lookup uses authenticated internal catalog endpoint`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()

        server.expect(requestTo("http://catalog-service:8082/api/v1/internal/catalog/offerings/$offeringId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Internal-Secret", "m8-internal-secret"))
            .andExpect(header("X-Service-Name", "order-service"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "offeringId": "$offeringId",
                      "providerId": "$providerId",
                      "name": "M8 Dog Food",
                      "price": 199.00,
                      "status": "ACTIVE",
                      "stockQuantity": 25
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val snapshot = RemoteCatalogModuleApi(
            restTemplate,
            "http://catalog-service:8082",
            "m8-internal-secret"
        ).offering(offeringId)

        assertEquals(offeringId, snapshot.offeringId)
        assertEquals(providerId, snapshot.providerId)
        assertEquals("M8 Dog Food", snapshot.name)
        assertEquals(BigDecimal("199.00"), snapshot.price)
        assertEquals("ACTIVE", snapshot.status)
        assertEquals(25, snapshot.stockQuantity)
        server.verify()
    }
}
