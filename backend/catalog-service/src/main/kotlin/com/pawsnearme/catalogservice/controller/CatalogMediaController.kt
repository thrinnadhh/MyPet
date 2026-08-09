package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.service.CatalogMediaAccessDeniedException
import com.pawsnearme.catalogservice.service.CatalogMediaService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/v1/catalog/offerings")
class CatalogMediaController(
    private val catalogMediaService: CatalogMediaService,
) {
    data class OfferingMediaResponse(
        val offering: Offering,
        val imageUrl: String,
    )

    @PostMapping("/{offeringId}/media")
    fun uploadOfferingImage(
        @PathVariable offeringId: UUID,
        @RequestPart("file") file: MultipartFile,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<OfferingMediaResponse> {
        val requesterId = xUserId?.let {
            runCatching { UUID.fromString(it) }
                .getOrElse { throw CatalogMediaAccessDeniedException("Invalid requester identity") }
        }
        val stored = catalogMediaService.storeOfferingImage(
            offeringId = offeringId,
            requesterId = requesterId,
            requesterRole = xUserRole,
            file = file,
        )
        return ResponseEntity.ok(OfferingMediaResponse(stored.offering, stored.imageUrl))
    }

    @GetMapping("/media/{filename:.+}")
    fun getOfferingImage(@PathVariable filename: String): ResponseEntity<Any> {
        val loaded = catalogMediaService.loadPublicImage(filename)
        return ResponseEntity.ok()
            .contentType(loaded.mediaType)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
            .body(loaded.resource)
    }

    @ExceptionHandler(CatalogMediaAccessDeniedException::class)
    fun handleAccessDenied(ex: CatalogMediaAccessDeniedException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message))
}
