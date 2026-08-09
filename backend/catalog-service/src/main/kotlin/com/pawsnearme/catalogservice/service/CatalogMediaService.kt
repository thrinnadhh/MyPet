package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.catalogservice.repository.ProviderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

class CatalogMediaAccessDeniedException(message: String) : RuntimeException(message)

@Service
class CatalogMediaService(
    private val offeringRepository: OfferingRepository,
    private val providerRepository: ProviderRepository,
    @Value("\${catalog.media.dir:./catalog-media}")
    private val mediaDir: String,
    @Value("\${catalog.public-base-url:http://localhost:8080}")
    private val publicBaseUrl: String,
) {
    private val maxBytes = 5L * 1024L * 1024L
    private val allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")

    init {
        Files.createDirectories(root())
    }

    data class StoredMedia(
        val offering: Offering,
        val imageUrl: String,
    )

    data class LoadedMedia(
        val resource: Resource,
        val mediaType: MediaType,
    )

    @Transactional
    fun storeOfferingImage(
        offeringId: UUID,
        requesterId: UUID?,
        requesterRole: String?,
        file: MultipartFile,
    ): StoredMedia {
        val offering = offeringRepository.findById(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
        verifyOwnership(offering, requesterId, requesterRole)
        validateFile(file)

        val mimeType = file.contentType!!.lowercase()
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val filename = "${UUID.randomUUID()}.$extension"
        val destination = safePath(filename)
        Files.write(destination, file.bytes)

        val previousUrl = offering.imageUrl
        val imageUrl = "${publicBaseUrl.trimEnd('/')}/api/v1/catalog/offerings/media/$filename"
        offering.imageUrl = imageUrl
        val saved = offeringRepository.save(offering)
        deleteManagedPrevious(previousUrl, filename)
        return StoredMedia(saved, imageUrl)
    }

    fun loadPublicImage(filename: String): LoadedMedia {
        requireSafeFilename(filename)
        val path = safePath(filename)
        if (!Files.isRegularFile(path)) {
            throw NoSuchElementException("Catalog media not found")
        }
        val mediaType = when (filename.substringAfterLast('.', "").lowercase()) {
            "png" -> MediaType.IMAGE_PNG
            "webp" -> MediaType.parseMediaType("image/webp")
            else -> MediaType.IMAGE_JPEG
        }
        return LoadedMedia(FileSystemResource(path), mediaType)
    }

    private fun verifyOwnership(offering: Offering, requesterId: UUID?, requesterRole: String?) {
        if (requesterRole == "ADMIN") return
        if (
            requesterRole == "MERCHANT" &&
            requesterId != null &&
            providerRepository.existsByProviderIdAndOwnerUserId(offering.providerId, requesterId)
        ) return
        throw CatalogMediaAccessDeniedException("Access denied to offering media")
    }

    private fun validateFile(file: MultipartFile) {
        require(!file.isEmpty) { "Image file is required" }
        require(file.size <= maxBytes) { "Image exceeds 5MB limit" }
        val mimeType = file.contentType?.lowercase()
            ?: throw IllegalArgumentException("Image content type is required")
        require(mimeType in allowedMimeTypes) { "Only JPEG, PNG and WebP images are supported" }
    }

    private fun root(): Path = Paths.get(mediaDir).toAbsolutePath().normalize()

    private fun safePath(filename: String): Path {
        requireSafeFilename(filename)
        val path = root().resolve(filename).normalize()
        require(path.startsWith(root())) { "Invalid media path" }
        return path
    }

    private fun requireSafeFilename(filename: String) {
        require(filename.matches(Regex("^[0-9a-fA-F-]{36}\\.(jpg|png|webp)$"))) { "Invalid media filename" }
    }

    private fun deleteManagedPrevious(previousUrl: String?, replacementFilename: String) {
        val previousFilename = previousUrl
            ?.substringAfterLast('/')
            ?.takeIf { it != replacementFilename }
            ?: return
        if (!runCatching { requireSafeFilename(previousFilename) }.isSuccess) return
        runCatching { Files.deleteIfExists(safePath(previousFilename)) }
    }
}
