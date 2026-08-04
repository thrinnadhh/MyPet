package com.pawsnearme.contentservice.service

import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideLike
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import com.pawsnearme.contentservice.repository.GuideArticleRepository
import com.pawsnearme.contentservice.repository.GuideLikeRepository
import com.pawsnearme.contentservice.repository.GuideWriterRepository
import com.pawsnearme.contentservice.repository.PromoBannerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

class ContentAccessDeniedException(message: String) : RuntimeException(message)
class ContentNotFoundException(message: String) : RuntimeException(message)

data class GuideLikeResult(val liked: Boolean, val likeCount: Long)

@Service
@Transactional
class ContentService(
    private val bannerRepo: PromoBannerRepository,
    private val guideRepo: GuideArticleRepository,
    private val writerRepo: GuideWriterRepository,
    private val likeRepo: GuideLikeRepository,
) {
    @Transactional(readOnly = true)
    fun listActiveBanners(): List<PromoBanner> =
        bannerRepo.findByActiveTrueOrderBySortOrderAsc().filter { it.status == "ACTIVE" }

    @Transactional(readOnly = true)
    fun listGuides(category: String?): List<GuideArticle> =
        if (category.isNullOrBlank()) guideRepo.findByPublishedTrueOrderByCreatedAtDesc()
        else guideRepo.findByPublishedTrueAndCategoryOrderByCreatedAtDesc(category)

    @Transactional(readOnly = true)
    fun listGuidesByAuthor(authorUserId: UUID): List<GuideArticle> =
        guideRepo.findByAuthorUserIdOrderByCreatedAtDesc(authorUserId)

    fun upsertBanner(banner: PromoBanner): PromoBanner {
        if (banner.providerId == null && banner.bidAmount == null && banner.status == "PENDING_BID") {
            banner.status = "ACTIVE"
        }
        banner.updatedAt = Instant.now()
        return bannerRepo.save(banner)
    }

    fun upsertGuide(article: GuideArticle, callerRole: String?): GuideArticle {
        val authorId = article.authorUserId
            ?: throw IllegalArgumentException("Author is required to publish a guide.")

        if (callerRole?.uppercase() == "ADMIN") {
            article.authorName = article.authorName.ifBlank { "MyPet Editorial Team" }
            article.companyName = article.companyName.ifBlank { "MyPet" }
        } else {
            val writer = writerRepo.findByUserId(authorId)
                ?: throw ContentAccessDeniedException("Guide writer access has not been granted.")
            if (writer.accessStatus != "ACTIVE") {
                throw ContentAccessDeniedException("Guide writer access is not active.")
            }
            article.authorName = writer.authorName
            article.companyName = writer.companyName
        }

        article.title = article.title.trim()
        article.summary = article.summary.trim()
        article.body = article.body?.trim()
        article.category = article.category.trim().lowercase()
        article.readMinutes = article.readMinutes.coerceIn(1, 30)
        article.updatedAt = Instant.now()
        return guideRepo.save(article)
    }

    @Transactional(readOnly = true)
    fun listWriters(): List<GuideWriter> = writerRepo.findAllByOrderByCreatedAtDesc()

    @Transactional(readOnly = true)
    fun getWriterForUser(userId: UUID): GuideWriter =
        writerRepo.findByUserId(userId)
            ?: throw ContentAccessDeniedException("Guide writer access has not been granted.")

    fun grantWriter(
        userId: UUID,
        email: String,
        authorName: String,
        companyName: String,
    ): GuideWriter {
        val writer = writerRepo.findByUserId(userId) ?: GuideWriter(
            userId = userId,
            email = email.trim(),
            authorName = authorName.trim(),
            companyName = companyName.trim(),
        )
        writer.email = email.trim()
        writer.authorName = authorName.trim()
        writer.companyName = companyName.trim()
        writer.accessStatus = "ACTIVE"
        writer.updatedAt = Instant.now()
        return writerRepo.save(writer)
    }

    fun setWriterAccess(writerId: UUID, active: Boolean): GuideWriter {
        val writer = writerRepo.findById(writerId)
            .orElseThrow { ContentNotFoundException("Guide writer was not found.") }
        writer.accessStatus = if (active) "ACTIVE" else "REVOKED"
        writer.updatedAt = Instant.now()
        return writerRepo.save(writer)
    }

    fun revokeWriter(writerId: UUID) {
        setWriterAccess(writerId, false)
    }

    fun toggleGuideLike(articleId: UUID, userId: UUID): GuideLikeResult {
        val article = guideRepo.findById(articleId)
            .orElseThrow { ContentNotFoundException("Guide article was not found.") }
        val existing = likeRepo.findByArticleIdAndUserId(articleId, userId)
        val liked = if (existing == null) {
            likeRepo.save(GuideLike(articleId = articleId, userId = userId))
            article.likeCount += 1
            true
        } else {
            likeRepo.delete(existing)
            article.likeCount = (article.likeCount - 1).coerceAtLeast(0)
            false
        }
        guideRepo.save(article)
        return GuideLikeResult(liked = liked, likeCount = article.likeCount)
    }
}
