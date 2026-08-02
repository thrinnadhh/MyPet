package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.service.MedicalDocumentAccessDeniedException
import com.pawsnearme.appointmentservice.service.MedicalDocumentLink
import com.pawsnearme.appointmentservice.service.MedicalDocumentService
import com.pawsnearme.appointmentservice.service.MedicalDocumentView
import com.pawsnearme.appointmentservice.service.MedicalUploadReservation
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

data class MedicalUploadReservationRequest(val appointmentId: UUID)

@RestController
@RequestMapping("/api/v1/appointments/medical-documents")
class MedicalDocumentController(
    private val medicalDocumentService: MedicalDocumentService
) {
    @PostMapping("/reservations")
    fun reserveUpload(
        @RequestParam appointmentId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<MedicalUploadReservation> = ResponseEntity.ok(
        medicalDocumentService.reserveUpload(appointmentId, requireUser(userId), role)
    )

    @PostMapping("/upload")
    fun upload(
        @RequestParam uploadToken: String,
        @RequestParam("file") file: MultipartFile,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
        @RequestHeader("X-Trace-Id", required = false) traceId: String?
    ): ResponseEntity<MedicalDocumentView> = ResponseEntity.status(HttpStatus.CREATED).body(
        medicalDocumentService.storeUpload(
            uploadToken = uploadToken,
            file = file,
            actorId = requireUser(userId),
            actorRole = role,
            traceId = requestId ?: traceId ?: UUID.randomUUID().toString()
        )
    )

    @GetMapping
    fun listMine(
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<List<MedicalDocumentView>> =
        ResponseEntity.ok(medicalDocumentService.listMine(requireUser(userId)))

    @PostMapping("/{documentId}/signed-link")
    fun signedLink(
        @PathVariable documentId: UUID,
        @RequestParam(defaultValue = "inline") disposition: String,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
        @RequestHeader("X-Trace-Id", required = false) traceId: String?
    ): ResponseEntity<MedicalDocumentLink> = ResponseEntity.ok(
        medicalDocumentService.issueSignedLink(
            documentId,
            requireUser(userId),
            role,
            disposition,
            requestId ?: traceId ?: UUID.randomUUID().toString()
        )
    )

    @GetMapping("/{documentId}/content")
    fun content(
        @PathVariable documentId: UUID,
        @RequestParam token: String,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
        @RequestHeader("X-Trace-Id", required = false) traceId: String?
    ): ResponseEntity<ByteArray> {
        val content = medicalDocumentService.readSigned(
            documentId,
            token,
            requestId ?: traceId ?: UUID.randomUUID().toString()
        )
        val disposition = if (content.disposition == "attachment") {
            ContentDisposition.attachment().filename(content.filename, StandardCharsets.UTF_8).build()
        } else {
            ContentDisposition.inline().filename(content.filename, StandardCharsets.UTF_8).build()
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.mimeType))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.bytes)
    }

    private fun requireUser(value: String?): UUID = try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw MedicalDocumentAccessDeniedException("Valid authenticated user context is required.")
    }

    @ExceptionHandler(MedicalDocumentAccessDeniedException::class)
    fun accessDenied(error: MedicalDocumentAccessDeniedException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to error.message))
}
