package com.pawsnearme.contentservice.repository

import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PromoBannerRepository : JpaRepository<PromoBanner, UUID> {
    fun findByActiveTrueOrderBySortOrderAsc(): List<PromoBanner>
}

interface GuideArticleRepository : JpaRepository<GuideArticle, UUID> {
    fun findByPublishedTrueAndCategoryOrderByCreatedAtDesc(category: String): List<GuideArticle>
    fun findByPublishedTrueOrderByCreatedAtDesc(): List<GuideArticle>
}

interface GuideWriterRepository : JpaRepository<GuideWriter, UUID> {
    fun findByAccessStatus(accessStatus: String): List<GuideWriter>
    fun findByUserId(userId: UUID): GuideWriter?
}
