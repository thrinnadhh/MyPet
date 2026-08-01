package com.pawsnearme.orderservice.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class RemoteDiscoveryModuleApiTests {
    @Test
    fun `serviceability request carries gateway trust secret`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val expectedUrl =
            "http://discovery-service:8083/api/v1/service-regions/check" +
                "?city=Tirupati&latitude=13.6288&longitude=79.4192&pincode=517501"

        server.expect(requestTo(expectedUrl))
            .andExpect(header("X-Internal-Gateway-Secret", "m8-gateway-secret"))
            .andRespond(
                withSuccess(
                    """{"serviceable":true,"reason":"Inside Tirupati delivery region"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = RemoteDiscoveryModuleApi(
            restTemplate,
            "http://discovery-service:8083",
            "m8-gateway-secret"
        ).checkServiceability(
            city = " Tirupati ",
            latitude = 13.6288,
            longitude = 79.4192,
            pincode = "517501"
        )

        assertTrue(result.serviceable)
        assertEquals("Inside Tirupati delivery region", result.reason)
        server.verify()
    }
}
