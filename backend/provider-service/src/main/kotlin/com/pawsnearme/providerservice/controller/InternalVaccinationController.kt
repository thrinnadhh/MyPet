package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

@RestController
@RequestMapping("/api/v1/internal")
class InternalVaccinationController(
    private val vaccinationReminderRepository: VaccinationReminderRepository,
    @Value("\${internal.api.secret}")
    private val internalSecret: String,
) {
    @GetMapping("/vaccination-reminders")
    fun listEnabled(
        @RequestHeader("X-Internal-Secret") secret: String,
    ): ResponseEntity<List<Map<String, Any>>> {
        if (!MessageDigest.isEqual(secret.toByteArray(), internalSecret.toByteArray())) {
            throw ProviderAccessDeniedException("Forbidden")
        }
        val rows = vaccinationReminderRepository.findByEnabledTrue().map { reminder ->
            mapOf(
                "reminderId" to reminder.reminderId.toString(),
                "ownerId" to reminder.ownerId.toString(),
                "petId" to reminder.petId.toString(),
                "vaccineName" to reminder.vaccineName,
                "dueDate" to reminder.dueDate.toString(),
                "enabled" to reminder.enabled,
            )
        }
        return ResponseEntity.ok(rows)
    }
}
