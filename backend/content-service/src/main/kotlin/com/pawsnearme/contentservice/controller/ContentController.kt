package com.pawsnearme.contentservice.controller

import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import com.pawsnearme.contentservice.service.BannerAuctionService
import com.pawsnearme.contentservice.service.BannerBidAccessDeniedException
import com.pawsnearme.contentservice.service.ContentAccessDeniedException
import com.pawsnearme.contentservice.service.ContentNotFoundException
import com.pawsnearme.contentservice.service.ContentService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BannerDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: String,
    val durationSec: Int,
    val sortOrder: Int,
    val active: Boolean,
    val imageUrl: String?,
    val targetType: String,
    val targetValue: String?,
    val startsAt: String?,
    val endsAt: String?,
)

data class GuideDto(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val readMinutes: Int,
    val authorName: String,
    val companyName: String,
    val likeCount: Long,
    val createdAt: String,
)

data class UpsertBannerRequest(
    @field:NotBlank val title: String,
    @field:NotBlank val subtitle: String,
    val accentColor: String = "#F97316",
    val durationSec: Int = 5,
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val imageUrl: String? = null,
    val targetType: String = "NONE",
    val targetValue: String? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
)

data class UpsertGuideRequest(
    @field:NotBlank val category: String,
    @field:NotBlank val title: String,
    @field:NotBlank val summary: String,
    val body: String? = null,
    val readMinutes: Int = 3,
    val published: Boolean = true,
    val authorName: String = "",
    val companyName: String = "",
)

data class GrantWriterRequest(
    val userId: UUID,
    @field:NotBlank val email: String,
    @field:NotBlank val authorName: String,
    @field:NotBlank val companyName: String,
)

data class GuideWriterStatusRequest(val active: Boolean)

data class GuideLikeDto(val liked: Boolean, val likeCount: Long)

data class SubmitBannerBidRequest(
    @field:NotNull val providerId: UUID,
    val slotOrder: Int,
    @field:NotNull val bidAmount: BigDecimal,
    @field:NotNull val windowEndsAt: Instant,
)

data class BannerBidDto(
    val id: String,
    val providerId: String,
    val slotOrder: Int,
    val bidAmount: BigDecimal,
    val windowEndsAt: String,
    val status: String,
)

@RestController
@RequestMapping("/api/v1/content")
class ContentController(
    private val contentService: ContentService,
    private val bannerAuctionService: BannerAuctionService,
) {

    @GetMapping("/banners")
    fun listBanners(): ResponseEntity<List<BannerDto>> =
        ResponseEntity.ok(contentService.listActiveBanners().map { it.toDto() })

    @GetMapping("/guides")
    fun listGuides(@RequestParam(required = false) category: String?): ResponseEntity<List<GuideDto>> =
        ResponseEntity.ok(contentService.listGuides(category).map { it.toDto() })

    @GetMapping("/guides/mine")
    fun listMyGuides(
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
    ): ResponseEntity<List<GuideDto>> {
        val callerId = userId?.let(UUID::fromString)
            ?: throw IllegalArgumentException("X-User-Id header is required.")
        return ResponseEntity.ok(contentService.listGuidesByAuthor(callerId).map { it.toDto() })
    }

    @GetMapping("/guides/categories")
    fun listCategories(): ResponseEntity<List<Map<String, String>>> =
        ResponseEntity.ok(
            listOf(
                mapOf("id" to "puppy-kitten", "label" to "1–2 months", "description" to "Feeding, sleep, and first vet visits"),
                mapOf("id" to "skin", "label" to "Skin issues", "description" to "Rashes, dryness, and when to see a vet"),
                mapOf("id" to "ticks-odor", "label" to "Ticks & odor", "description" to "Prevention, grooming, and home care"),
            )
        )

    @PostMapping("/banners")
    fun createBanner(
        @RequestHeader(value = "X-User-Role", required = false) userRole: String?,
        @Valid @RequestBody request: UpsertBannerRequest,
    ): ResponseEntity<BannerDto> {
        requireAdmin(userRole)
        val saved = contentService.upsertBanner(request.toBanner())
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toDto())
    }

    @PutMapping("/banners/{bannerId}")
    fun updateBanner(
        @PathVariable bannerId: UUID,
        @RequestHeader(value = "X-User-Role", required = false) userRole: String?,
        @Valid @RequestBody request: UpsertBannerRequest,
    ): ResponseEntity<BannerDto> {
        requireAdmin(userRole)
        return ResponseEntity.ok(contentService.upsertBanner(request.toBanner(bannerId)).toDto())
    }

    @PostMapping("/guides")
    fun createGuide(
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-User-Role", required = false) userRole: String?,
        @RequestBody request: UpsertGuideRequest,
    ): ResponseEntity<GuideDto> {
        val saved = contentService.upsertGuide(
            GuideArticle(
                category = request.category,
                title = request.title,
                summary = request.summary,
                body = request.body,
                readMinutes = request.readMinutes,
                published = request.published,
                authorUserId = userId?.let(UUID::fromString),
                authorName = request.authorName,
                companyName = request.companyName,
            ),
            callerRole = userRole,
        )
        return ResponseEntity.ok(saved.toDto())
    }

    @PostMapping("/guides/{articleId}/likes")
    fun toggleGuideLike(
        @PathVariable articleId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
    ): ResponseEntity<GuideLikeDto> {
        val callerId = userId?.let(UUID::fromString)
            ?: throw ContentAccessDeniedException("Sign in to like a guide.")
        val result = contentService.toggleGuideLike(articleId, callerId)
        return ResponseEntity.ok(GuideLikeDto(result.liked, result.likeCount))
    }

    @GetMapping("/guides/writers")
    fun listWriters(): ResponseEntity<List<GuideWriter>> =
        ResponseEntity.ok(contentService.listWriters())

    @GetMapping("/guides/writers/me")
    fun getMyWriterAccess(
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
    ): ResponseEntity<GuideWriter> {
        val callerId = userId?.let(UUID::fromString)
            ?: throw IllegalArgumentException("X-User-Id header is required.")
        return ResponseEntity.ok(contentService.getWriterForUser(callerId))
    }

    @PostMapping("/guides/writers")
    fun grantWriter(@RequestBody request: GrantWriterRequest): ResponseEntity<GuideWriter> =
        ResponseEntity.ok(
            contentService.grantWriter(
                userId = request.userId,
                email = request.email,
                authorName = request.authorName,
                companyName = request.companyName,
            )
        )

    @PutMapping("/guides/writers/{writerId}/status")
    fun setWriterStatus(
        @PathVariable writerId: UUID,
        @RequestBody request: GuideWriterStatusRequest,
    ): ResponseEntity<GuideWriter> =
        ResponseEntity.ok(contentService.setWriterAccess(writerId, request.active))

    @DeleteMapping("/guides/writers/{writerId}")
    fun revokeWriter(@PathVariable writerId: UUID): ResponseEntity<Map<String, String>> {
        contentService.revokeWriter(writerId)
        return ResponseEntity.ok(mapOf("status" to "REVOKED"))
    }

    @PostMapping("/banners/bids")
    fun submitBannerBid(
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-User-Role", required = false) userRole: String?,
        @RequestBody request: SubmitBannerBidRequest,
    ): ResponseEntity<BannerBidDto> {
        val callerId = userId?.let(UUID::fromString)
            ?: throw IllegalArgumentException("X-User-Id header is required.")
        val saved = bannerAuctionService.submitBid(
            providerId = request.providerId,
            slotOrder = request.slotOrder,
            bidAmount = request.bidAmount,
            windowEndsAt = request.windowEndsAt,
            callerId = callerId,
            callerRole = userRole,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toBidDto())
    }

    @GetMapping("/banners/bids")
    fun listBannerBids(
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-User-Role", required = false) userRole: String?,
    ): ResponseEntity<List<BannerBidDto>> {
        val callerId = userId?.let(UUID::fromString)
            ?: throw IllegalArgumentException("X-User-Id header is required.")
        return ResponseEntity.ok(bannerAuctionService.listBids(callerId, userRole).map { it.toBidDto() })
    }

    @DeleteMapping("/banners/bids/{bidId}")
    fun cancelBannerBid(
        @PathVariable bidId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-User-Role", required = false) userRole: String?,
    ): ResponseEntity<Map<String, String>> {
        val callerId = userId?.let(UUID::fromString)
            ?: throw IllegalArgumentException("X-User-Id header is required.")
        bannerAuctionService.cancelBid(bidId, callerId, userRole)
        return ResponseEntity.ok(mapOf("status" to "CANCELLED"))
    }

    @GetMapping("/banners/auction-outcomes")
    fun listAuctionOutcomes(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(bannerAuctionService.listAuctionOutcomes())

    @ExceptionHandler(BannerBidAccessDeniedException::class, ContentAccessDeniedException::class)
    fun handleAccessDenied(ex: RuntimeException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message.orEmpty()))

    @ExceptionHandler(ContentNotFoundException::class)
    fun handleNotFound(ex: ContentNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to ex.message.orEmpty()))

    private fun requireAdmin(userRole: String?) {
        if (!userRole.equals("ADMIN", ignoreCase = true)) {
            throw ContentAccessDeniedException("Admin role is required to manage banners.")
        }
    }

    private fun UpsertBannerRequest.toBanner(bannerId: UUID? = null) = PromoBanner(
        bannerId = bannerId,
        title = title,
        subtitle = subtitle,
        accentColor = accentColor,
        durationSec = durationSec,
        sortOrder = sortOrder,
        active = active,
        imageUrl = imageUrl,
        targetType = targetType,
        targetValue = targetValue,
        startsAt = startsAt,
        endsAt = endsAt,
    )

    private fun com.pawsnearme.contentservice.model.BannerBid.toBidDto() = BannerBidDto(
        id = bidId!!.toString(),
        providerId = providerId.toString(),
        slotOrder = slotOrder,
        bidAmount = bidAmount,
        windowEndsAt = windowEndsAt.toString(),
        status = status,
    )

    private fun PromoBanner.toDto() = BannerDto(
        id = bannerId!!.toString(),
        title = title,
        subtitle = subtitle,
        accent = accentColor,
        durationSec = durationSec,
        sortOrder = sortOrder,
        active = active,
        imageUrl = imageUrl,
        targetType = targetType,
        targetValue = targetValue,
        startsAt = startsAt?.toString(),
        endsAt = endsAt?.toString(),
    )

    private fun GuideArticle.toDto() = GuideDto(
        id = articleId!!.toString(),
        category = category,
        title = title,
        summary = summary,
        readMinutes = readMinutes,
        authorName = authorName,
        companyName = companyName,
        likeCount = likeCount,
        createdAt = createdAt.toString(),
    )
}
