package com.pawsnearme.providerservice.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MediaUploadAccessDeniedException(message: String) : RuntimeException(message)

@Service
class MediaUploadService(
    @Value("\${provider.uploads.dir:./uploads}")
    private val uploadDir: String,
    @Value("\${provider.public-base-url:http://localhost:8081}")
    private val publicBaseUrl: String,
) {
    private val allowedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf",
    )
    private val maxBytes = 10L * 1024 * 1024
    private val pendingTokens = ConcurrentHashMap<String, PendingUpload>()

    data class PendingUpload(
        val userId: UUID,
        val createdAt: Instant,
    )

    data class UploadReservation(
        val uploadToken: String,
        val uploadUrl: String,
    )

    data class StoredUpload(
        val fileUrl: String,
        val storedFilename: String,
    )

    init {
        Files.createDirectories(Paths.get(uploadDir))
    }

    fun reserveUpload(userId: UUID): UploadReservation {
        val token = UUID.randomUUID().toString()
        pendingTokens[token] = PendingUpload(userId, Instant.now())
        val base = publicBaseUrl.trimEnd('/')
        return UploadReservation(
            uploadToken = token,
            uploadUrl = "$base/api/v1/providers/upload-file",
        )
    }

    fun storeUpload(userId: UUID, uploadToken: String, file: MultipartFile): StoredUpload {
        val pending = pendingTokens[uploadToken]
            ?: throw IllegalArgumentException("Invalid or expired upload token.")
        if (pending.userId != userId) {
            throw MediaUploadAccessDeniedException("Upload token does not belong to this user.")
        }
        pendingTokens.remove(uploadToken)
        if (pending.createdAt.isBefore(Instant.now().minusSeconds(900))) {
            throw IllegalArgumentException("Upload token has expired.")
        }

        if (file.isEmpty) {
            throw IllegalArgumentException("File is required.")
        }
        if (file.size > maxBytes) {
            throw IllegalArgumentException("File exceeds 10MB limit.")
        }

        val mimeType = file.contentType?.lowercase()
            ?: throw IllegalArgumentException("File mime type is required.")
        if (mimeType !in allowedMimeTypes) {
            throw IllegalArgumentException("Unsupported file type.")
        }

        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            else -> "jpg"
        }
        val storedFilename = "${UUID.randomUUID()}.$extension"
        val uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize()
        val destination = uploadRoot.resolve(storedFilename).normalize()
        if (!destination.startsWith(uploadRoot)) {
            throw IllegalArgumentException("Invalid upload path.")
        }
        Files.write(destination, file.bytes)

        val fileUrl = "${publicBaseUrl.trimEnd('/')}/uploads/$storedFilename"
        return StoredUpload(fileUrl = fileUrl, storedFilename = storedFilename)
    }

    fun rejectUnsafeFilename(filename: String) {
        if (filename.contains("..") || filename.contains('/') || filename.contains('\\')) {
            throw IllegalArgumentException("Unsafe filename rejected.")
        }
    }
}
