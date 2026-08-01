package com.pawsnearme.notificationservice.module

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.time.LocalDate
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class NotificationRemoteModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProviderModuleApi::class)
    fun remoteProviderModuleApi(
        restOperations: RestOperations,
        objectMapper: ObjectMapper,
        @Value("\${provider.service.url:http://localhost:8081}") baseUrl: String,
        @Value("\${internal.api.secret}") internalSecret: String
    ): ProviderModuleApi = RemoteProviderModuleApi(restOperations, objectMapper, baseUrl, internalSecret)
}

class RemoteProviderModuleApi(
    private val restOperations: RestOperations,
    private val objectMapper: ObjectMapper,
    private val baseUrl: String,
    private val internalSecret: String
) : ProviderModuleApi {
    override fun ownerUserId(providerId: UUID): UUID? = runCatching {
        val response = restOperations.getForObject("$baseUrl/api/v1/providers/$providerId", Map::class.java)
        response?.get("ownerUserId")?.toString()?.let(UUID::fromString)
    }.getOrNull()

    override fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot> {
        val headers = HttpHeaders().apply { set("X-Internal-Secret", internalSecret) }
        val response = restOperations.exchange(
            "$baseUrl/api/v1/internal/vaccination-reminders",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )
        val rows: List<Map<String, Any>> = objectMapper.readValue(
            response.body ?: "[]",
            object : TypeReference<List<Map<String, Any>>>() {}
        )
        return rows.map { row ->
            VaccinationReminderSnapshot(
                reminderId = UUID.fromString(row["reminderId"].toString()),
                ownerId = UUID.fromString(row["ownerId"].toString()),
                petId = UUID.fromString(row["petId"].toString()),
                vaccineName = row["vaccineName"].toString(),
                dueDate = LocalDate.parse(row["dueDate"].toString()),
                enabled = row["enabled"] as? Boolean ?: true
            )
        }
    }
}
