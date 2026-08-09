package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.catalogservice.repository.ProviderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

class CatalogMediaAccessDeniedException(message: String) : RuntimeException(message)

@Service
class CatalogMediaService(
    private val offeringRepository: OfferingRepository,
    private val providerRepository: ProviderRepository,
    private val s3Client: S3Client,
    @Value("\${storage.catalog-media.bucket:mypet-catalog-media-local}")
    private val bucket: String,
    @Value("\${catalog.public-base-url:http://localhost:8080}")
    private val publicBaseUrl: String,
) {
    private val maxBytes = 5L * 1024L * 1024L
    private val allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")

    data class StoredMedia(
        val offering: Offering,
        val imageUrl: String,
    )

    data class LoadedMedia(
        val bytes: ByteArray,
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
        val objectKey = objectKey(filename)
        val previousUrl = offering.imageUrl

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(mimeType)
                .cacheControl("public, max-age=2592000, immutable")
                .build(),
            RequestBody.fromBytes(file.bytes),
        )

        try {
            val imageUrl = "${publicBaseUrl.trimEnd('/')}/api/v1/catalog/offerings/media/$filename"
            offering.imageUrl = imageUrl
            val saved = offeringRepository.save(offering)
            deleteManagedPrevious(previousUrl, filename)
            return StoredMedia(saved, imageUrl)
        } catch (exception: Exception) {
            runCatching { deleteObject(filename) }
            throw exception
        }
    }

    fun loadPublicImage(filename: String): LoadedMedia {
        requireSafeFilename(filename)
        val response = try {
            s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(filename))
                    .build(),
            )
        } catch (exception: NoSuchKeyException) {
            throw NoSuchElementException("Catalog media not found")
        }
        val mediaType = when (filename.substringAfterLast('.', "").lowercase()) {
            "png" -> MediaType.IMAGE_PNG
            "webp" -> MediaType.parseMediaType("image/webp")
            else -> MediaType.IMAGE_JPEG
        }
        return LoadedMedia(response.asByteArray(), mediaType)
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

    private fun objectKey(filename: String): String {
        requireSafeFilename(filename)
        return "catalog-media/$filename"
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
        runCatching { deleteObject(previousFilename) }
    }

    private fun deleteObject(filename: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(filename))
                .build(),
        )
    }
}
