package com.pawsnearme.providerservice.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@RestController
@RequestMapping("/api/v1/providers")
class MediaController(
    @Value("\${provider.public-base-url:http://localhost:8081}")
    private val publicBaseUrl: String,
) {

    private val uploadDir = "./uploads"

    init {
        val dir = File(uploadDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    @PostMapping("/upload-url")
    fun getUploadUrl(
        @RequestParam filename: String,
    ): ResponseEntity<Map<String, String>> {
        val uniqueName = "${UUID.randomUUID()}_$filename"
        val base = publicBaseUrl.trimEnd('/')
        val uploadUrl = "$base/api/v1/providers/upload-file?filename=$uniqueName"
        val fileUrl = "$base/uploads/$uniqueName"

        return ResponseEntity.ok(
            mapOf(
                "uploadUrl" to uploadUrl,
                "fileUrl" to fileUrl,
            ),
        )
    }

    @PostMapping("/upload-file")
    fun uploadFile(
        @RequestParam filename: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Map<String, String>> {
        val destinationPath = Paths.get(uploadDir, filename)
        Files.write(destinationPath, file.bytes)
        return ResponseEntity.ok(
            mapOf(
                "status" to "SUCCESS",
                "filename" to filename,
            ),
        )
    }
}
