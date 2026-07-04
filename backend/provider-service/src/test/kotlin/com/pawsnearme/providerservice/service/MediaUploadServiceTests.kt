package com.pawsnearme.providerservice.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockMultipartFile
import java.util.UUID

class MediaUploadServiceTests {

    private val service = MediaUploadService(
        uploadDir = "./build/test-uploads",
        publicBaseUrl = "http://localhost:8080",
    )

    private val userId = UUID.randomUUID()

    @Test
    fun `rejectUnsafeFilename - path traversal - throws`() {
        assertThrows<IllegalArgumentException> {
            service.rejectUnsafeFilename("../secrets.txt")
        }
    }

    @Test
    fun `storeUpload - unauthenticated token mismatch - throws access denied`() {
        val reservation = service.reserveUpload(userId)
        val file = MockMultipartFile("file", "doc.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        assertThrows<MediaUploadAccessDeniedException> {
            service.storeUpload(UUID.randomUUID(), reservation.uploadToken, file)
        }
    }

    @Test
    fun `storeUpload - valid pdf upload - returns gateway file url`() {
        val reservation = service.reserveUpload(userId)
        val file = MockMultipartFile("file", "ignored.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        val stored = service.storeUpload(userId, reservation.uploadToken, file)

        assertTrue(stored.fileUrl.startsWith("http://localhost:8080/uploads/"))
        assertFalse(stored.storedFilename.contains(".."))
    }
}
