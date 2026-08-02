package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.MedicalDocument
import com.pawsnearme.appointmentservice.model.MedicalDocumentAccessLog
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.repository.MedicalDocumentAccessLogRepository
import com.pawsnearme.appointmentservice.repository.MedicalDocumentRepository
import com.pawsnearme.common.module.ProviderModuleApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MedicalDocumentAccessDeniedException(message: String) : RuntimeException(message)

data class MedicalUploadReservation(
    val uploadToken: String,
    val uploadUrl: String,
    val expiresAt: Instant
)

data class MedicalDocumentView(
    val documentId: UUID,
    val appointmentId: UUID,
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: String,
    val createdAt: Instant
)

data class MedicalDocumentLink(
    val documentId: UUID,
    val url: String,
    val expiresAt: Instant
)

data class MedicalDocumentContent(
    val bytes: ByteArray,
    val mimeType: String,
    val filename: String,
    val disposition: String
)

@Service
class MedicalDocumentService(
    private val appointmentRepository: AppointmentRepository,
    private val documentRepository: MedicalDocumentRepository,
    private val accessLogRepository: MedicalDocumentAccessLogRepository,
    private val providerModule: ProviderModuleApi,
    @Value("\${appointment.medical-documents.dir:./private-medical-documents}") private val storageDir: String,
    @Value("\${appointment.medical-documents.public-base-url:http://localhost:8084}") private val publicBaseUrl: String,
    @Value("\${MEDICAL_DOCUMENT_SIGNING_KEY:local-development-key}") private val signingKey: String
) {
    private data class PendingUpload(
        val appointmentId: UUID,
        val actorId: UUID,
        val actorRole: String?,
        val expiresAt: Instant
    )

    private val pendingUploads = ConcurrentHashMap<String, PendingUpload>()
    private val root: Path = Paths.get(storageDir).toAbsolutePath().normalize()
    private val allowedMimeTypes = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")

    init {
        require(signingKey.length >= 16) { "Medical-document signing key must contain at least 16 characters." }
        Files.createDirectories(root)
    }

    fun reserveUpload(appointmentId: UUID, actorId: UUID, actorRole: String?): MedicalUploadReservation {
        authorizeAppointment(appointmentId, actorId, actorRole)
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(600)
        pendingUploads[token] = PendingUpload(appointmentId, actorId, actorRole, expiresAt)
        return MedicalUploadReservation(
            uploadToken = token,
            uploadUrl = "${publicBaseUrl.trimEnd('/')}/api/v1/appointments/medical-documents/upload",
            expiresAt = expiresAt
        )
    }

    @Transactional
    fun storeUpload(
        uploadToken: String,
        file: MultipartFile,
        actorId: UUID,
        actorRole: String?,
        traceId: String
    ): MedicalDocumentView {
        val pending = pendingUploads.remove(uploadToken)
            ?: throw IllegalArgumentException("Invalid or already-used medical upload token.")
        if (pending.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Medical upload token has expired.")
        if (pending.actorId != actorId) throw MedicalDocumentAccessDeniedException("Upload token belongs to another user.")
        authorizeAppointment(pending.appointmentId, actorId, actorRole)
        validateFile(file)

        val appointment = appointmentRepository.findById(pending.appointmentId)
            .orElseThrow { IllegalArgumentException("Appointment not found.") }
        val extension = extension(file.contentType!!.lowercase())
        val storageKey = "${UUID.randomUUID()}.$extension"
        val destination = root.resolve(storageKey).normalize()
        if (!destination.startsWith(root)) throw IllegalArgumentException("Invalid private storage path.")
        Files.write(destination, file.bytes)

        val saved = documentRepository.save(
            MedicalDocument(
                appointmentId = pending.appointmentId,
                ownerUserId = appointment.customerId,
                uploaderUserId = actorId,
                originalFilename = sanitizeFilename(file.originalFilename),
                storageKey = storageKey,
                mimeType = file.contentType!!.lowercase(),
                sizeBytes = file.size
            )
        )
        log(saved.documentId, actorId, "UPLOAD", traceId)
        return saved.toView()
    }

    @Transactional(readOnly = true)
    fun listMine(actorId: UUID): List<MedicalDocumentView> =
        documentRepository.findByOwnerUserIdOrderByCreatedAtDesc(actorId).map { it.toView() }

    @Transactional
    fun issueSignedLink(
        documentId: UUID,
        actorId: UUID,
        actorRole: String?,
        disposition: String,
        traceId: String
    ): MedicalDocumentLink {
        val document = documentRepository.findById(documentId)
            .orElseThrow { IllegalArgumentException("Medical document not found.") }
        authorizeDocument(document, actorId, actorRole)
        require(document.status == "AVAILABLE") { "Medical document is not available." }
        val safeDisposition = if (disposition.equals("attachment", ignoreCase = true)) "attachment" else "inline"
        val expiresAt = Instant.now().plusSeconds(300)
        val payload = listOf(document.documentId, actorId, expiresAt.epochSecond, safeDisposition).joinToString("|")
        val token = encode(payload) + "." + encode(hmac(payload.toByteArray(StandardCharsets.UTF_8)))
        val url = "${publicBaseUrl.trimEnd('/')}/api/v1/appointments/medical-documents/${document.documentId}/content?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
        log(document.documentId, actorId, "SIGNED_URL_ISSUED", traceId)
        return MedicalDocumentLink(document.documentId, url, expiresAt)
    }

    @Transactional
    fun readSigned(documentId: UUID, token: String, traceId: String): MedicalDocumentContent {
        val parts = token.split('.', limit = 2)
        if (parts.size != 2) throw MedicalDocumentAccessDeniedException("Invalid medical-document token.")
        val payload = String(decode(parts[0]), StandardCharsets.UTF_8)
        val expected = hmac(payload.toByteArray(StandardCharsets.UTF_8))
        val actual = decode(parts[1])
        if (!MessageDigest.isEqual(expected, actual)) throw MedicalDocumentAccessDeniedException("Invalid medical-document signature.")
        val values = payload.split('|')
        if (values.size != 4) throw MedicalDocumentAccessDeniedException("Invalid medical-document token payload.")
        if (UUID.fromString(values[0]) != documentId) throw MedicalDocumentAccessDeniedException("Medical-document token mismatch.")
        val actorId = UUID.fromString(values[1])
        if (Instant.ofEpochSecond(values[2].toLong()).isBefore(Instant.now())) {
            throw MedicalDocumentAccessDeniedException("Medical-document link has expired.")
        }
        val document = documentRepository.findById(documentId)
            .orElseThrow { IllegalArgumentException("Medical document not found.") }
        require(document.status == "AVAILABLE") { "Medical document is not available." }
        val path = root.resolve(document.storageKey).normalize()
        if (!path.startsWith(root) || !Files.exists(path)) throw IllegalStateException("Medical document content is unavailable.")
        val disposition = if (values[3] == "attachment") "attachment" else "inline"
        log(documentId, actorId, if (disposition == "attachment") "DOWNLOAD" else "VIEW", traceId)
        return MedicalDocumentContent(Files.readAllBytes(path), document.mimeType, document.originalFilename, disposition)
    }

    private fun authorizeAppointment(appointmentId: UUID, actorId: UUID, actorRole: String?) {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { IllegalArgumentException("Appointment not found.") }
        val role = actorRole?.uppercase()
        val allowed = role == "ADMIN" || appointment.customerId == actorId ||
            (role in setOf("PROVIDER", "MERCHANT") && providerModule.ownerUserId(appointment.providerId) == actorId)
        if (!allowed) throw MedicalDocumentAccessDeniedException("Medical document access is not permitted for this appointment.")
    }

    private fun authorizeDocument(document: MedicalDocument, actorId: UUID, actorRole: String?) {
        if (document.ownerUserId == actorId || actorRole.equals("ADMIN", ignoreCase = true)) return
        authorizeAppointment(document.appointmentId, actorId, actorRole)
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) throw IllegalArgumentException("Medical document is required.")
        if (file.size > MAX_BYTES) throw IllegalArgumentException("Medical document exceeds the 10 MB limit.")
        val mimeType = file.contentType?.lowercase() ?: throw IllegalArgumentException("Medical document type is required.")
        if (mimeType !in allowedMimeTypes) throw IllegalArgumentException("Only PDF, JPEG, PNG and WebP medical documents are supported.")
        val bytes = file.bytes
        val validSignature = when (mimeType) {
            "application/pdf" -> bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("%PDF".toByteArray())
            "image/png" -> bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
            "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            "image/webp" -> bytes.size >= 12 && String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
            else -> false
        }
        if (!validSignature) throw IllegalArgumentException("Medical document content does not match its declared type.")
    }

    private fun sanitizeFilename(value: String?): String {
        val candidate = value?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        return candidate.take(255).ifBlank { "medical-document" }
    }

    private fun extension(mimeType: String) = when (mimeType) {
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    private fun log(documentId: UUID, actorId: UUID, action: String, traceId: String) {
        accessLogRepository.save(
            MedicalDocumentAccessLog(
                documentId = documentId,
                actorUserId = actorId,
                action = action,
                traceId = traceId.take(160).ifBlank { UUID.randomUUID().toString() }
            )
        )
    }

    private fun hmac(value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        doFinal(value)
    }

    private fun encode(value: String): String = encode(value.toByteArray(StandardCharsets.UTF_8))
    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun MedicalDocument.toView() = MedicalDocumentView(
        documentId = documentId,
        appointmentId = appointmentId,
        originalFilename = originalFilename,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        status = status,
        createdAt = createdAt
    )

    companion object {
        private const val MAX_BYTES = 10L * 1024 * 1024
    }
}
