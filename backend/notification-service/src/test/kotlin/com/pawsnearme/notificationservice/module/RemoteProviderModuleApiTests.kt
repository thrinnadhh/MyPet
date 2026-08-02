package com.pawsnearme.notificationservice.module

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestOperations
import java.util.UUID

class RemoteProviderModuleApiTests {
    @Test
    fun `owner lookup presents service and gateway credentials`() {
        val restOperations: RestOperations = mock()
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        whenever(
            restOperations.exchange(
                eq("http://provider/api/v1/internal/providers/$providerId/owner"),
                eq(HttpMethod.GET),
                any<HttpEntity<Void>>(),
                eq(Map::class.java)
            )
        ).thenReturn(ResponseEntity.ok(mapOf("ownerUserId" to ownerId.toString())))
        val adapter = RemoteProviderModuleApi(
            restOperations,
            ObjectMapper(),
            "http://provider",
            "service-secret",
            "gateway-secret"
        )

        assertEquals(ownerId, adapter.ownerUserId(providerId))

        val entity = argumentCaptor<HttpEntity<Void>>()
        verify(restOperations).exchange(
            eq("http://provider/api/v1/internal/providers/$providerId/owner"),
            eq(HttpMethod.GET),
            entity.capture(),
            eq(Map::class.java)
        )
        assertEquals("service-secret", entity.firstValue.headers.getFirst("X-Internal-Secret"))
        assertEquals("gateway-secret", entity.firstValue.headers.getFirst("X-Internal-Gateway-Secret"))
    }
}
