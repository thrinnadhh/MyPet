package com.pawsnearme.contentservice.module

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.VaccinationReminderSnapshot
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestOperations
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class ContentRemoteModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProviderModuleApi::class)
    fun remoteProviderModuleApi(
        restOperations: RestOperations,
        @Value("\${PROVIDER_SERVICE_URL:http://localhost:8081}") baseUrl: String,
        @Value("\${gateway.trust.secret:}") gatewayTrustSecret: String
    ): ProviderModuleApi = RemoteProviderModuleApi(restOperations, baseUrl, gatewayTrustSecret)
}

class RemoteProviderModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val gatewayTrustSecret: String
) : ProviderModuleApi {
    override fun ownerUserId(providerId: UUID): UUID? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/providers/$providerId",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply {
                if (gatewayTrustSecret.isNotBlank()) {
                    set("X-Internal-Gateway-Secret", gatewayTrustSecret)
                }
            }),
            Map::class.java
        ).body
        response?.get("ownerUserId")?.toString()?.let(UUID::fromString)
    }.getOrNull()

    override fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot> = emptyList()
}
