package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.model.MedicalDocument
import com.pawsnearme.appointmentservice.model.MedicalDocumentAccessLog
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.repository.MedicalDocumentAccessLogRepository
import com.pawsnearme.appointmentservice.repository.MedicalDocumentRepository
import com.pawsnearme.common.module.ProviderModuleApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.nio.file.Files
import java.util.Optional
import java.util.UUID

class MedicalDocumentServiceTests {
    private val appointmentRepository: AppointmentRepository = mock()
    private val documentRepository: MedicalDocumentRepository = mock()
    private val accessLogRepository: MedicalDocumentAccessLogRepository = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val root = Files.createTempDirectory("mypet-medical-test")
    private val service = MedicalDocumentService(
        appointmentRepository,
        documentRepository,
        accessLogRepository,
        providerModule,
        root.toString(),
        "http://localhost:8084",
        "0123456789abcdef0123456789abcdef"
    )

    private val customerId = UUID.randomUUID()
    private val providerOwnerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val appointmentId = UUID.randomUUID()

    @Test
    fun `customer uploads a private PDF and each access is audited`() {
        val appointment = appointment()
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment))
        whenever(documentRepository.save(any<MedicalDocument>())).thenAnswer { it.getArgument(0) }
        whenever(accessLogRepository.save(any<MedicalDocumentAccessLog>())).thenAnswer { it.getArgument(0) }
        val reservation = service.reserveUpload(appointmentId, customerId, "CUSTOMER")
        val file = MockMultipartFile(
            "file",
            "report.pdf",
            "application/pdf",
            "%PDF-1.7 private report".toByteArray()
        )

        val stored = service.storeUpload(reservation.uploadToken, file, customerId, "CUSTOMER", "trace-upload")

        assertEquals("report.pdf", stored.originalFilename)
        assertEquals("application/pdf", stored.mimeType)
        val document = argumentCaptor<MedicalDocument>()
        verify(documentRepository).save(document.capture())
        whenever(documentRepository.findById(document.firstValue.documentId)).thenReturn(Optional.of(document.firstValue))
        val link = service.issueSignedLink(document.firstValue.documentId, customerId, "CUSTOMER", "inline", "trace-link")
        val token = link.url.substringAfter("token=")
        val content = service.readSigned(document.firstValue.documentId, java.net.URLDecoder.decode(token, Charsets.UTF_8), "trace-view")
        assertTrue(content.bytes.copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
        verify(accessLogRepository, org.mockito.kotlin.times(3)).save(any<MedicalDocumentAccessLog>())
    }

    @Test
    fun `provider owner is authorized but unrelated provider is denied`() {
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment()))
        whenever(providerModule.ownerUserId(providerId)).thenReturn(providerOwnerId)
        service.reserveUpload(appointmentId, providerOwnerId, "PROVIDER")

        assertThrows<MedicalDocumentAccessDeniedException> {
            service.reserveUpload(appointmentId, UUID.randomUUID(), "PROVIDER")
        }
    }

    @Test
    fun `rejects declared content that does not match file signature`() {
        whenever(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment()))
        val reservation = service.reserveUpload(appointmentId, customerId, "CUSTOMER")
        val fakePdf = MockMultipartFile("file", "fake.pdf", "application/pdf", "not-a-pdf".toByteArray())

        assertThrows<IllegalArgumentException> {
            service.storeUpload(reservation.uploadToken, fakePdf, customerId, "CUSTOMER", "trace")
        }
    }

    private fun appointment() = Appointment(
        appointmentId = appointmentId,
        customerId = customerId,
        providerId = providerId,
        offeringId = UUID.randomUUID(),
        slotId = UUID.randomUUID(),
        petId = UUID.randomUUID(),
        status = AppointmentStatus.COMPLETED,
        priceAmount = BigDecimal("500.00")
    )
}
