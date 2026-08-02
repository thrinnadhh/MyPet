package com.pawsnearme.paymentservice.module

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
class LoyaltyProviderModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProviderModuleApi::class)
    fun providerModuleApi(
        restOperations: RestOperations,
        @Value("\${PROVIDER_SERVICE_URL:http://localhost:8081}") baseUrl: String,
        @Value("\${internal.api.secret:}") secret: String
    ): ProviderModuleApi = RemoteLoyaltyProviderModuleApi(restOperations, baseUrl, secret)
}

class RemoteLoyaltyProviderModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val internalSecret: String
) : ProviderModuleApi {
    override fun ownerUserId(providerId: UUID): UUID? = runCatching {
        val body = restOperations.exchange(
            "$baseUrl/api/v1/providers/$providerId",
            HttpMethod.GET,
            HttpEntity<Any>(headers()),
            Map::class.java
        ).body
        body?.get("ownerUserId")?.toString()?.let(UUID::fromString)
    }.getOrNull()

    override fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot> = emptyList()

    private fun headers() = HttpHeaders().apply {
        if (internalSecret.isNotBlank()) {
            set("X-Internal-Secret", internalSecret)
            set("X-Service-Name", "payment-service")
        }
    }
}
