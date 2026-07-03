package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.VaccinationReminder
import com.pawsnearme.providerservice.repository.ProfileRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LocaleUpdateRequest(@field:NotBlank val locale: String)

data class VaccinationReminderDto(
    val reminderId: UUID,
    val petId: UUID,
    val vaccineName: String,
    val dueDate: String,
    val clinicName: String?,
    val enabled: Boolean,
)

data class UpsertVaccinationReminderRequest(
    val petId: UUID,
    @field:NotBlank val vaccineName: String,
    val dueDate: String,
    val clinicName: String? = null,
    val enabled: Boolean = true,
)

@RestController
@RequestMapping("/api/v1")
class PreferenceController(
    private val profileRepository: ProfileRepository,
    private val vaccinationReminderRepository: VaccinationReminderRepository,
) {
    @PatchMapping("/profiles/me/locale")
    fun updateLocale(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: LocaleUpdateRequest,
    ): ResponseEntity<Map<String, String>> {
        val profile = profileRepository.findById(UUID.fromString(userId))
            .orElseThrow { NoSuchElementException("Profile not found") }
        profile.preferredLocale = request.locale
        profileRepository.save(profile)
        return ResponseEntity.ok(mapOf("locale" to profile.preferredLocale))
    }

    @GetMapping("/profiles/me/locale")
    fun getLocale(@RequestHeader("X-User-Id") userId: String): ResponseEntity<Map<String, String>> {
        val profile = profileRepository.findById(UUID.fromString(userId))
            .orElseThrow { NoSuchElementException("Profile not found") }
        return ResponseEntity.ok(mapOf("locale" to profile.preferredLocale))
    }

    @GetMapping("/vaccination-reminders")
    fun listReminders(@RequestHeader("X-User-Id") userId: String): ResponseEntity<List<VaccinationReminderDto>> {
        val ownerId = UUID.fromString(userId)
        return ResponseEntity.ok(
            vaccinationReminderRepository.findByOwnerId(ownerId).map { it.toDto() }
        )
    }

    @PostMapping("/vaccination-reminders")
    fun upsertReminder(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: UpsertVaccinationReminderRequest,
    ): ResponseEntity<VaccinationReminderDto> {
        val ownerId = UUID.fromString(userId)
        val saved = vaccinationReminderRepository.save(
            VaccinationReminder(
                petId = request.petId,
                ownerId = ownerId,
                vaccineName = request.vaccineName,
                dueDate = LocalDate.parse(request.dueDate),
                clinicName = request.clinicName,
                enabled = request.enabled,
            )
        )
        return ResponseEntity.ok(saved.toDto())
    }

    @PatchMapping("/vaccination-reminders/{reminderId}")
    fun updateReminder(
        @RequestHeader("X-User-Id") userId: String,
        @PathVariable reminderId: UUID,
        @RequestBody request: Map<String, Any>,
    ): ResponseEntity<VaccinationReminderDto> {
        val reminder = vaccinationReminderRepository.findById(reminderId)
            .orElseThrow { NoSuchElementException("Reminder not found") }
        if (reminder.ownerId.toString() != userId) throw ProviderAccessDeniedException("Access denied")
        if (request.containsKey("enabled")) reminder.enabled = request["enabled"] as Boolean
        reminder.updatedAt = Instant.now()
        return ResponseEntity.ok(vaccinationReminderRepository.save(reminder).toDto())
    }

    private fun VaccinationReminder.toDto() = VaccinationReminderDto(
        reminderId = reminderId!!,
        petId = petId,
        vaccineName = vaccineName,
        dueDate = dueDate.toString(),
        clinicName = clinicName,
        enabled = enabled,
    )
}
