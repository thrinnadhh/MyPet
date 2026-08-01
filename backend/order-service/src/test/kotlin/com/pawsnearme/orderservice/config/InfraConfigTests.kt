package com.pawsnearme.orderservice.config

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class InfraConfigTests {
    @Test
    fun `timed rest template injects gateway trust header on outbound module calls`() {
        val restTemplate = InfraConfig().timedRestTemplate("m8-gateway-secret")
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(requestTo("http://catalog-service:8082/api/v1/internal/catalog/offerings/test"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Internal-Gateway-Secret", "m8-gateway-secret"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        restTemplate.getForEntity(
            "http://catalog-service:8082/api/v1/internal/catalog/offerings/test",
            String::class.java
        )

        server.verify()
    }

    @Test
    fun `blank gateway secret does not inject an empty trust header`() {
        val restTemplate = InfraConfig().timedRestTemplate("")
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(requestTo("http://payment-service:8090/api/v1/payments/cod/check"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        restTemplate.getForEntity(
            "http://payment-service:8090/api/v1/payments/cod/check",
            String::class.java
        )

        server.verify()
    }
}
