package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.service.MediaUploadAccessDeniedException
import com.pawsnearme.providerservice.service.MediaUploadService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/providers")
class MediaController(
    private val mediaUploadService: MediaUploadService,
) {
    @PostMapping("/upload-url")
    fun getUploadUrl(
        @RequestHeader("X-User-Id", required = false) userId: String?,
    ): ResponseEntity<Any> {
        if (userId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }
        val reservation = mediaUploadService.reserveUpload(UUID.fromString(userId))
        return ResponseEntity.ok(
            mapOf(
                "uploadToken" to reservation.uploadToken,
                "uploadUrl" to reservation.uploadUrl,
            ),
        )
    }

    @PostMapping("/upload-file")
    fun uploadFile(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestParam uploadToken: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Any> {
        if (userId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Missing authenticated user context."))
        }
        return try {
            val stored = mediaUploadService.storeUpload(UUID.fromString(userId), uploadToken, file)
            ResponseEntity.ok(
                mapOf(
                    "status" to "SUCCESS",
                    "fileUrl" to stored.fileUrl,
                    "filename" to stored.storedFilename,
                ),
            )
        } catch (ex: MediaUploadAccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to ex.message))
        }
    }
}
