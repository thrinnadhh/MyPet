package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.CreateMedicalReportRequest
import com.pawsnearme.providerservice.model.MedicalReport
import com.pawsnearme.providerservice.model.MedicalReportDto
import com.pawsnearme.providerservice.repository.MedicalReportRepository
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pets")
class MedicalReportController(
    private val medicalReportRepository: MedicalReportRepository,
    private val petRepository: PetRepository,
    private val vaccinationReminderRepository: VaccinationReminderRepository
) {

    @GetMapping("/{petId}/medical-reports")
    fun getMedicalReports(
        @PathVariable petId: UUID,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<List<MedicalReportDto>> {
        if (userIdHeader.isNull_or_blank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val authenticatedUserId = try {
            UUID.fromString(userIdHeader)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        // Verify Pet Ownership
        val petOpt = petRepository.findById(petId)
        if (petOpt.isPresent) {
            val pet = petOpt.get()
            if (pet.ownerId != authenticatedUserId) {
                throw ProviderAccessDeniedException("Access Denied: Cross-customer access to medical records is prohibited")
            }
        }

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
        if (userIdHeader.isNull_or_blank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val authenticatedUserId = try {
            UUID.fromString(userIdHeader)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        // Verify Pet Ownership if pet exists
        val petOpt = petRepository.findById(petId)
        if (petOpt.isPresent) {
            val pet = petOpt.get()
            if (pet.ownerId != authenticatedUserId) {
                throw ProviderAccessDeniedException("Access Denied: You do not own this pet")
            }
        }

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
        if (userIdHeader.isNull_or_blank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val authenticatedUserId = try {
            UUID.fromString(userIdHeader)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        val petOpt = petRepository.findById(petId)
        if (petOpt.isPresent) {
            val pet = petOpt.get()
            if (pet.ownerId != authenticatedUserId) {
                throw ProviderAccessDeniedException("Access Denied: You do not own this pet")
            }
        }

        val reminders = vaccinationReminderRepository.findByEnabledTrue()
            .filter { it.petId == petId && it.ownerId == authenticatedUserId }
            .map { r ->
                mapOf(
                    "reminderId" to r.reminderId,
                    "petId" to r.petId,
                    "vaccineName" to r.vaccineName,
                    "dueDate" to r.dueDate.toString(),
                    "clinicName" to r.clinicName,
                    "enabled" to r.enabled
                )
            }
        return ResponseEntity.ok(reminders)
    }

    private fun toDto(report: MedicalReport): MedicalReportDto {
        val reportIdStr = report.reportId.toString()
        val expiresAt = Instant.now().epochSecond + 3600
        val signedUrl = "https://s3.amazonaws.com/pawsnearme-private/reports/${report.objectKey}?sig=sec_${reportIdStr.take(8)}&expires=$expiresAt"
        return MedicalReportDto(
            reportId = report.reportId!!,
            petId = report.petId,
            ownerId = report.ownerId,
            title = report.title,
            category = report.category,
            labOrClinicName = report.labOrClinicName,
            doctorName = report.doctorName,
            signedUrl = signedUrl,
            createdAt = report.createdAt
        )
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
