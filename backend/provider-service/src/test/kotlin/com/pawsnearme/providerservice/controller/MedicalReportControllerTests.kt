package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.CreateMedicalReportRequest
import com.pawsnearme.providerservice.model.MedicalReport
import com.pawsnearme.providerservice.model.Pet
import com.pawsnearme.providerservice.repository.MedicalReportRepository
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

class MedicalReportControllerTests {

    private lateinit var medicalReportRepository: MedicalReportRepository
    private lateinit var petRepository: PetRepository
    private lateinit var vaccinationReminderRepository: VaccinationReminderRepository
    private lateinit var controller: MedicalReportController

    private val ownerId = UUID.randomUUID()
    private val petId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        medicalReportRepository = mock(MedicalReportRepository::class.java)
        petRepository = mock(PetRepository::class.java)
        vaccinationReminderRepository = mock(VaccinationReminderRepository::class.java)

        controller = MedicalReportController(
            medicalReportRepository,
            petRepository,
            vaccinationReminderRepository
        )
    }

    @Test
    fun `getMedicalReports should return signed URLs for authorized pet owner`() {
        val pet = Pet(petId = petId, ownerId = ownerId, name = "Bruno")
        `when`(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        val report = MedicalReport(
            reportId = UUID.randomUUID(),
            petId = petId,
            ownerId = ownerId,
            title = "Annual Blood Test",
            category = "BLOOD_TEST",
            labOrClinicName = "City Vet Labs",
            doctorName = "Dr. K. Srinivas",
            objectKey = "reports/bruno_blood_2026.pdf"
        )
        `when`(medicalReportRepository.findAllByPetIdOrderByCreatedAtDesc(petId))
            .thenReturn(listOf(report))

        val response = controller.getMedicalReports(petId, ownerId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(1, response.body!!.size)
        val dto = response.body!![0]
        assertEquals("Annual Blood Test", dto.title)
        assertTrue(dto.signedUrl.contains("reports/bruno_blood_2026.pdf"))
        assertTrue(dto.signedUrl.contains("sig="))
    }

    @Test
    fun `getMedicalReports should throw ProviderAccessDeniedException for unauthorized cross customer request`() {
        val actualOwnerId = UUID.randomUUID()
        val maliciousUserId = UUID.randomUUID()

        val pet = Pet(petId = petId, ownerId = actualOwnerId, name = "Bruno")
        `when`(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        assertThrows(ProviderAccessDeniedException::class.java) {
            controller.getMedicalReports(petId, maliciousUserId.toString())
        }
    }

    @Test
    fun `createMedicalReport should save and return DTO with signed URL for pet owner`() {
        val pet = Pet(petId = petId, ownerId = ownerId, name = "Bruno")
        `when`(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        val request = CreateMedicalReportRequest(
            title = "Rabies Vaccination Certificate",
            category = "VACCINATION",
            labOrClinicName = "City Pet Hospital",
            doctorName = "Dr. K. Srinivas",
            objectKey = "reports/rabies_cert_2026.pdf"
        )

        val savedReport = MedicalReport(
            reportId = UUID.randomUUID(),
            petId = petId,
            ownerId = ownerId,
            title = request.title,
            category = request.category,
            labOrClinicName = request.labOrClinicName,
            doctorName = request.doctorName,
            objectKey = request.objectKey
        )

        `when`(medicalReportRepository.save(any(MedicalReport::class.java))).thenReturn(savedReport)

        val response = controller.createMedicalReport(petId, ownerId.toString(), request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertEquals("Rabies Vaccination Certificate", response.body!!.title)
        assertTrue(response.body!!.signedUrl.contains("rabies_cert_2026.pdf"))
    }
}
