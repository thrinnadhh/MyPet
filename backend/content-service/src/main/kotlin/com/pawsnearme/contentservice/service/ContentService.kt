package com.pawsnearme.contentservice.service

import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import com.pawsnearme.contentservice.repository.GuideArticleRepository
import com.pawsnearme.contentservice.repository.GuideWriterRepository
import com.pawsnearme.contentservice.repository.PromoBannerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

class ContentAccessDeniedException(message: String) : RuntimeException(message)

@Service
@Transactional
class ContentService(
    private val bannerRepo: PromoBannerRepository,
    private val guideRepo: GuideArticleRepository,
    private val writerRepo: GuideWriterRepository,
) {
    @Transactional(readOnly = true)
    fun listActiveBanners(): List<PromoBanner> =
        bannerRepo.findByActiveTrueOrderBySortOrderAsc().filter { it.status == "ACTIVE" }

    @Transactional(readOnly = true)
    fun listGuides(category: String?): List<GuideArticle> =
        if (category.isNullOrBlank()) guideRepo.findByPublishedTrueOrderByCreatedAtDesc()
        else guideRepo.findByPublishedTrueAndCategoryOrderByCreatedAtDesc(category)

    fun upsertBanner(banner: PromoBanner): PromoBanner {
        // Direct content-management banners have no provider or auction bid and
        // are publishable immediately. Auction submissions retain their own
        // PENDING/WON lifecycle in BannerAuctionService.
        if (banner.providerId == null && banner.bidAmount == null && banner.status == "PENDING_BID") {
            banner.status = "ACTIVE"
        }
        banner.updatedAt = Instant.now()
        return bannerRepo.save(banner)
    }

    fun upsertGuide(article: GuideArticle, callerRole: String?): GuideArticle {
        val authorId = article.authorUserId
            ?: throw IllegalArgumentException("Author is required to publish a guide.")
        if (callerRole?.uppercase() != "ADMIN") {
            val writer = writerRepo.findByUserId(authorId)
                ?: throw ContentAccessDeniedException("Guide writer access has not been granted.")
            if (writer.accessStatus != "ACTIVE") {
                throw ContentAccessDeniedException("Guide writer access is not active.")
            }
        }
        article.updatedAt = Instant.now()
        return guideRepo.save(article)
    }

    @Transactional(readOnly = true)
    fun listWriters(): List<GuideWriter> = writerRepo.findByAccessStatus("ACTIVE")

    fun grantWriter(userId: UUID, email: String): GuideWriter =
        writerRepo.save(GuideWriter(userId = userId, email = email, accessStatus = "ACTIVE"))

    fun revokeWriter(writerId: UUID) {
        val writer = writerRepo.findById(writerId).orElseThrow()
        writer.accessStatus = "REVOKED"
        writerRepo.save(writer)
    }
}