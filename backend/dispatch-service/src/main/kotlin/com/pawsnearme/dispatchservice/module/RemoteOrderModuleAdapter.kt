package com.pawsnearme.dispatchservice.module

import com.pawsnearme.common.module.OrderModuleApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class DispatchRemoteModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(OrderModuleApi::class)
    fun remoteOrderModuleApi(
        restOperations: RestOperations,
        @Value("\${ORDER_SERVICE_URL:http://localhost:8084}") baseUrl: String,
        @Value("\${gateway.trust.secret:}") gatewayTrustSecret: String,
        @Value("\${internal.api.secret:}") internalApiSecret: String
    ): OrderModuleApi = RemoteOrderModuleApi(
        restOperations,
        baseUrl,
        gatewayTrustSecret,
        internalApiSecret
    )
}

class RemoteOrderModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val gatewayTrustSecret: String,
    private val internalApiSecret: String = ""
) : OrderModuleApi {
    override fun updateStatus(orderId: UUID, status: String, actorId: UUID, note: String?) {
        val url = UriComponentsBuilder
            .fromUriString("$baseUrl/internal/api/v1/orders/$orderId/status")
            .queryParam("status", status.trim().uppercase())
            .apply { if (!note.isNullOrBlank()) queryParam("note", note) }
            .build().encode().toUriString()
        val headers = HttpHeaders().apply {
            if (gatewayTrustSecret.isNotBlank()) {
                set("X-Internal-Gateway-Secret", gatewayTrustSecret)
            }
            if (internalApiSecret.isNotBlank()) {
                set("X-Internal-Api-Secret", internalApiSecret)
            }
            set("X-Captain-Id", actorId.toString())
        }
        restOperations.exchange(url, HttpMethod.PUT, HttpEntity<Any>(headers), Any::class.java)
    }
}
