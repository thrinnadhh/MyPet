package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.CreateMedicalReportRequest
import com.pawsnearme.providerservice.model.MedicalReport
import com.pawsnearme.providerservice.model.MedicalReportDto
import com.pawsnearme.providerservice.repository.MedicalReportRepository
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import com.pawsnearme.providerservice.service.MedicalReportStorageService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pets")
class MedicalReportController(
    private val medicalReportRepository: MedicalReportRepository,
    private val petRepository: PetRepository,
    private val vaccinationReminderRepository: VaccinationReminderRepository,
    private val medicalReportStorageService: MedicalReportStorageService,
) {

    @GetMapping("/{petId}/medical-reports")
    fun getMedicalReports(
        @PathVariable petId: UUID,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<List<MedicalReportDto>> {
        val authenticatedUserId = parseUserId(userIdHeader)
        requireOwnedPet(petId, authenticatedUserId)

        val reports = medicalReportRepository.findAllByPetIdOrderByCreatedAtDesc(petId)
            .filter { it.ownerId == authenticatedUserId }
            .map { toDto(it) }

        return ResponseEntity.ok(reports)
    }

    @PostMapping("/{petId}/medical-reports")
    fun createMedicalReport(
        @PathVariable petId: UUID,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?,
        @RequestBody request: CreateMedicalReportRequest
    ): ResponseEntity<MedicalReportDto> {
        val authenticatedUserId = parseUserId(userIdHeader)
        requireOwnedPet(petId, authenticatedUserId)
        medicalReportStorageService.validateObjectKey(authenticatedUserId, petId, request.objectKey)

        val report = MedicalReport(
            petId = petId,
            ownerId = authenticatedUserId,
            title = request.title,
            category = request.category,
            labOrClinicName = request.labOrClinicName,
            doctorName = request.doctorName,
            objectKey = request.objectKey
        )
        val saved = medicalReportRepository.save(report)
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved))
    }

    @GetMapping("/{petId}/vaccinations")
    fun getPetVaccinations(
        @PathVariable petId: UUID,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<List<Map<String, Any?>>> {
        val authenticatedUserId = parseUserId(userIdHeader)
        requireOwnedPet(petId, authenticatedUserId)

        val reminders = vaccinationReminderRepository.findByEnabledTrue()
            .filter { it.petId == petId && it.ownerId == authenticatedUserId }
            .map { reminder ->
                mapOf(
                    "reminderId" to reminder.reminderId,
                    "petId" to reminder.petId,
                    "vaccineName" to reminder.vaccineName,
                    "dueDate" to reminder.dueDate.toString(),
                    "clinicName" to reminder.clinicName,
                    "enabled" to reminder.enabled
                )
            }
        return ResponseEntity.ok(reminders)
    }

    private fun parseUserId(userIdHeader: String?): UUID {
        if (userIdHeader.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }
        return runCatching { UUID.fromString(userIdHeader) }
            .getOrElse { throw IllegalArgumentException("Invalid authenticated user context") }
    }

    private fun requireOwnedPet(petId: UUID, ownerId: UUID) {
        val pet = petRepository.findById(petId)
            .orElseThrow { NoSuchElementException("Pet not found") }
        if (pet.ownerId != ownerId) {
            throw ProviderAccessDeniedException(
                "Access denied: cross-customer access to medical records is prohibited"
            )
        }
    }

    private fun toDto(report: MedicalReport): MedicalReportDto = MedicalReportDto(
        reportId = requireNotNull(report.reportId),
        petId = report.petId,
        ownerId = report.ownerId,
        title = report.title,
        category = report.category,
        labOrClinicName = report.labOrClinicName,
        doctorName = report.doctorName,
        signedUrl = medicalReportStorageService.createDownloadUrl(
            report.ownerId,
            report.petId,
            report.objectKey,
        ),
        createdAt = report.createdAt
    )
}
