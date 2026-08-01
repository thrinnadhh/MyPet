package com.pawsnearme.appointmentservice.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

class RemoteCatalogModuleApiTests {
    @Test
    fun `slot adapter parses ISO timestamps from raw distributed response`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val slotId = UUID.randomUUID()
        val slotStart = Instant.parse("2026-08-01T10:30:00Z")
        val slotEnd = Instant.parse("2026-08-01T11:00:00Z")

        server.expect(requestTo("http://catalog-service:8082/api/v1/catalog/slots/$slotId"))
            .andExpect(header("X-Internal-Gateway-Secret", "m8-secret"))
            .andExpect(header("X-User-Role", "ADMIN"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "slotId": "$slotId",
                      "offeringId": "${UUID.randomUUID()}",
                      "slotStart": "$slotStart",
                      "slotEnd": "$slotEnd",
                      "status": "BOOKED"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val snapshot = RemoteCatalogModuleApi(
            restTemplate,
            "http://catalog-service:8082",
            "m8-secret"
        ).slot(slotId)

        assertNotNull(snapshot)
        assertEquals(slotId, snapshot?.slotId)
        assertEquals(slotStart, snapshot?.slotStart)
        assertEquals(slotEnd, snapshot?.slotEnd)
        assertEquals("BOOKED", snapshot?.status)
        server.verify()
    }

    @Test
    fun `slot adapter returns null for malformed timestamp instead of publishing wrong reminder time`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val slotId = UUID.randomUUID()

        server.expect(requestTo("http://catalog-service:8082/api/v1/catalog/slots/$slotId"))
            .andRespond(
                withSuccess(
                    """{"slotId":"$slotId","slotStart":"not-an-instant","status":"BOOKED"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val snapshot = RemoteCatalogModuleApi(
            restTemplate,
            "http://catalog-service:8082",
            ""
        ).slot(slotId)

        assertEquals(null, snapshot)
        server.verify()
    }
}
