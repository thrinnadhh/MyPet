package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.CreateMedicalReportRequest
import com.pawsnearme.providerservice.model.MedicalReport
import com.pawsnearme.providerservice.model.Pet
import com.pawsnearme.providerservice.repository.MedicalReportRepository
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import com.pawsnearme.providerservice.service.MedicalReportStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

class MedicalReportControllerTests {

    private lateinit var medicalReportRepository: MedicalReportRepository
    private lateinit var petRepository: PetRepository
    private lateinit var vaccinationReminderRepository: VaccinationReminderRepository
    private lateinit var medicalReportStorageService: MedicalReportStorageService
    private lateinit var controller: MedicalReportController

    private val ownerId = UUID.randomUUID()
    private val petId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        medicalReportRepository = mock(MedicalReportRepository::class.java)
        petRepository = mock(PetRepository::class.java)
        vaccinationReminderRepository = mock(VaccinationReminderRepository::class.java)
        medicalReportStorageService = mock(MedicalReportStorageService::class.java)

        controller = MedicalReportController(
            medicalReportRepository,
            petRepository,
            vaccinationReminderRepository,
            medicalReportStorageService,
        )
    }

    @Test
    fun `getMedicalReports returns genuine presigned URL for authorized pet owner`() {
        val pet = Pet(petId = petId, ownerId = ownerId, name = "Bruno")
        `when`(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        val objectKey = "medical-reports/$ownerId/$petId/bruno-blood-2026.pdf"
        val report = MedicalReport(
            reportId = UUID.randomUUID(),
            petId = petId,
            ownerId = ownerId,
            title = "Annual Blood Test",
            category = "BLOOD_TEST",
            labOrClinicName = "City Vet Labs",
            doctorName = "Dr. K. Srinivas",
            objectKey = objectKey,
        )
        `when`(medicalReportRepository.findAllByPetIdOrderByCreatedAtDesc(petId))
            .thenReturn(listOf(report))
        `when`(medicalReportStorageService.createDownloadUrl(ownerId, petId, objectKey))
            .thenReturn("https://private-bucket.example/bruno-blood-2026.pdf?X-Amz-Signature=test")

        val response = controller.getMedicalReports(petId, ownerId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(1, response.body!!.size)
        assertEquals("Annual Blood Test", response.body!![0].title)
        assertEquals(
            "https://private-bucket.example/bruno-blood-2026.pdf?X-Amz-Signature=test",
            response.body!![0].signedUrl,
        )
        verify(medicalReportStorageService).createDownloadUrl(ownerId, petId, objectKey)
    }

    @Test
    fun `getMedicalReports rejects unauthorized cross customer request`() {
        val actualOwnerId = UUID.randomUUID()
        val requestingUserId = UUID.randomUUID()

        val pet = Pet(petId = petId, ownerId = actualOwnerId, name = "Bruno")
        `when`(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        assertThrows(ProviderAccessDeniedException::class.java) {
            controller.getMedicalReports(petId, requestingUserId.toString())
        }
    }

    @Test
    fun `createMedicalReport validates scoped key and returns presigned URL`() {
        val pet = Pet(petId = petId, ownerId = ownerId, name = "Bruno")
        `when`(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        val objectKey = "medical-reports/$ownerId/$petId/rabies-cert-2026.pdf"
        val request = CreateMedicalReportRequest(
            title = "Rabies Vaccination Certificate",
            category = "VACCINATION",
            labOrClinicName = "City Pet Hospital",
            doctorName = "Dr. K. Srinivas",
            objectKey = objectKey,
        )

        val savedReport = MedicalReport(
            reportId = UUID.randomUUID(),
            petId = petId,
            ownerId = ownerId,
            title = request.title,
            category = request.category,
            labOrClinicName = request.labOrClinicName,
            doctorName = request.doctorName,
            objectKey = request.objectKey,
        )

        `when`(medicalReportRepository.save(any(MedicalReport::class.java))).thenReturn(savedReport)
        `when`(medicalReportStorageService.createDownloadUrl(ownerId, petId, objectKey))
            .thenReturn("https://private-bucket.example/rabies-cert-2026.pdf?X-Amz-Signature=test")

        val response = controller.createMedicalReport(petId, ownerId.toString(), request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertEquals("Rabies Vaccination Certificate", response.body!!.title)
        assertEquals(
            "https://private-bucket.example/rabies-cert-2026.pdf?X-Amz-Signature=test",
            response.body!!.signedUrl,
        )
        verify(medicalReportStorageService).validateObjectKey(ownerId, petId, objectKey)
    }
}
