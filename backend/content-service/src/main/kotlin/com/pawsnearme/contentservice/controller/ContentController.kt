package com.pawsnearme.contentservice.controller

import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import com.pawsnearme.contentservice.service.BannerAuctionService
import com.pawsnearme.contentservice.service.BannerBidAccessDeniedException
import com.pawsnearme.contentservice.service.ContentService
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
    val active: Boolean,
)

data class GuideDto(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val readMinutes: Int,
)

data class UpsertBannerRequest(
    @field:NotBlank val title: String,
    @field:NotBlank val subtitle: String,
    val accentColor: String = "#F97316",
    val durationSec: Int = 5,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

data class UpsertGuideRequest(
    @field:NotBlank val category: String,
    @field:NotBlank val title: String,
    @field:NotBlank val summary: String,
    val body: String? = null,
    val readMinutes: Int = 3,
    val published: Boolean = true,
)

data class GrantWriterRequest(
    val userId: UUID,
    @field:NotBlank val email: String,
)

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
        ResponseEntity.ok(
            contentService.listActiveBanners().map { it.toDto() }
        )

    @GetMapping("/guides")
    fun listGuides(@RequestParam(required = false) category: String?): ResponseEntity<List<GuideDto>> =
        ResponseEntity.ok(contentService.listGuides(category).map { it.toDto() })

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
    fun createBanner(@RequestBody request: UpsertBannerRequest): ResponseEntity<BannerDto> {
        val saved = contentService.upsertBanner(
            PromoBanner(
                title = request.title,
                subtitle = request.subtitle,
                accentColor = request.accentColor,
                durationSec = request.durationSec,
                sortOrder = request.sortOrder,
                active = request.active,
            )
        )
        return ResponseEntity.ok(saved.toDto())
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
            ),
            callerRole = userRole,
        )
        return ResponseEntity.ok(saved.toDto())
    }

    @GetMapping("/guides/writers")
    fun listWriters(): ResponseEntity<List<GuideWriter>> =
        ResponseEntity.ok(contentService.listWriters())

    @PostMapping("/guides/writers")
    fun grantWriter(@RequestBody request: GrantWriterRequest): ResponseEntity<GuideWriter> =
        ResponseEntity.ok(contentService.grantWriter(request.userId, request.email))

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
        return ResponseEntity.ok(
            bannerAuctionService.listBids(callerId, userRole).map { it.toBidDto() }
        )
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

    @ExceptionHandler(BannerBidAccessDeniedException::class)
    fun handleBidAccessDenied(ex: BannerBidAccessDeniedException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to ex.message.orEmpty()))

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
        active = active,
    )

    private fun GuideArticle.toDto() = GuideDto(
        id = articleId!!.toString(),
        category = category,
        title = title,
        summary = summary,
        readMinutes = readMinutes,
    )
}
