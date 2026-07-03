package com.pawsnearme.chatservice.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class ChatAttachmentService(
    @Value("\${chat.uploads.dir}") private val uploadDir: String,
    @Value("\${chat.public-base-url}") private val publicBaseUrl: String
) {
    private val allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
    private val maxBytes = 5L * 1024 * 1024

    init {
        Files.createDirectories(Paths.get(uploadDir))
    }

    fun uploadImage(file: MultipartFile): Pair<String, String> {
        if (file.isEmpty) {
            throw IllegalArgumentException("Image file is required.")
        }
        if (file.size > maxBytes) {
            throw IllegalArgumentException("Image exceeds 5MB limit.")
        }

        val mimeType = file.contentType?.lowercase()
            ?: throw IllegalArgumentException("Image mime type is required.")
        if (mimeType !in allowedMimeTypes) {
            throw IllegalArgumentException("Only JPEG, PNG, and WebP images are supported.")
        }

        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val filename = "${UUID.randomUUID()}.$extension"
        val destination = Paths.get(uploadDir, filename)
        Files.write(destination, file.bytes)

        val imageUrl = "${publicBaseUrl.trimEnd('/')}/uploads/chat/$filename"
        return imageUrl to mimeType
    }
}
