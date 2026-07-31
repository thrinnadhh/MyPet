package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.notificationservice.event.VaccinationReminderEvent
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.UUID

@Service
class VaccinationReminderSyncWorker(
    private val objectMapper: ObjectMapper,
    private val vaccinationReminderScheduler: VaccinationReminderScheduler,
    @Value("\${provider.service.url:http://localhost:8081}")
    private val providerServiceUrl: String,
    @Value("\${internal.api.secret}")
    private val internalSecret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 30_000)
    @SchedulerLock(
        name = "notification-sync-vaccination-reminders",
        lockAtMostFor = "PT30M",
        lockAtLeastFor = "PT5M"
    )
    fun syncEnabledReminders() {
        try {
            val headers = HttpHeaders().apply { set("X-Internal-Secret", internalSecret) }
            val response = restTemplate.exchange(
                "$providerServiceUrl/api/v1/internal/vaccination-reminders",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )
            val rows: List<Map<String, Any>> = objectMapper.readValue(
                response.body ?: "[]",
                object : TypeReference<List<Map<String, Any>>>() {},
            )
            rows.forEach { row ->
                vaccinationReminderScheduler.apply(
                    VaccinationReminderEvent(
                        eventId = UUID.randomUUID(),
                        eventType = "VaccinationReminderSynced",
                        reminderId = UUID.fromString(row["reminderId"].toString()),
                        ownerId = UUID.fromString(row["ownerId"].toString()),
                        petId = UUID.fromString(row["petId"].toString()),
                        vaccineName = row["vaccineName"].toString(),
                        dueDate = LocalDate.parse(row["dueDate"].toString()),
                        enabled = row["enabled"] as? Boolean ?: true,
                    ),
                )
            }
            log.info("Synced {} vaccination reminders from provider-service", rows.size)
        } catch (e: Exception) {
            log.warn("Vaccination reminder sync skipped: {}", e.message)
        }
    }
}
