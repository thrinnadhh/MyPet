package com.pawsnearme.contentservice.repository

import com.pawsnearme.contentservice.model.BannerBid
import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface PromoBannerRepository : JpaRepository<PromoBanner, UUID> {
    fun findByActiveTrueOrderBySortOrderAsc(): List<PromoBanner>
    fun findBySortOrder(sortOrder: Int): PromoBanner?
}

interface BannerBidRepository : JpaRepository<BannerBid, UUID> {
    fun findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId: UUID): List<BannerBid>
    fun findByStatusAndWindowEndsAtBefore(status: String, windowEndsAt: Instant): List<BannerBid>
    fun findBySlotOrderAndWindowEndsAtAndStatus(slotOrder: Int, windowEndsAt: Instant, status: String): List<BannerBid>

    @Query(
        """
        SELECT DISTINCT b.windowEndsAt FROM BannerBid b
        WHERE b.status = 'PENDING' AND b.windowEndsAt <= :now
        """
    )
    fun findExpiredWindowEnds(now: Instant): List<Instant>
}

interface GuideArticleRepository : JpaRepository<GuideArticle, UUID> {
    fun findByPublishedTrueAndCategoryOrderByCreatedAtDesc(category: String): List<GuideArticle>
    fun findByPublishedTrueOrderByCreatedAtDesc(): List<GuideArticle>
}

interface GuideWriterRepository : JpaRepository<GuideWriter, UUID> {
    fun findByAccessStatus(accessStatus: String): List<GuideWriter>
    fun findByUserId(userId: UUID): GuideWriter?
}
