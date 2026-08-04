package com.pawsnearme.contentservice.service

import com.pawsnearme.contentservice.model.GuideArticle
import com.pawsnearme.contentservice.model.GuideLike
import com.pawsnearme.contentservice.model.GuideWriter
import com.pawsnearme.contentservice.model.PromoBanner
import com.pawsnearme.contentservice.repository.GuideArticleRepository
import com.pawsnearme.contentservice.repository.GuideLikeRepository
import com.pawsnearme.contentservice.repository.GuideWriterRepository
import com.pawsnearme.contentservice.repository.PromoBannerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.Optional
import java.util.UUID

class ContentServiceTests {

    private val bannerRepo: PromoBannerRepository = mock()
    private val guideRepo: GuideArticleRepository = mock()
    private val writerRepo: GuideWriterRepository = mock()
    private val likeRepo: GuideLikeRepository = mock()
    private val service = ContentService(bannerRepo, guideRepo, writerRepo, likeRepo)

    private val merchantId = UUID.randomUUID()

    @Test
    fun `upsertBanner - direct admin content becomes active`() {
        whenever(bannerRepo.save(any())).thenAnswer { it.getArgument<PromoBanner>(0) }

        val saved = service.upsertBanner(
            PromoBanner(
                title = "Admin banner",
                subtitle = "Published immediately",
                active = true,
            )
        )

        assertEquals("ACTIVE", saved.status)
        verify(bannerRepo).save(check { assertEquals("ACTIVE", it.status) })
    }

    @Test
    fun `upsertBanner - auction content retains pending lifecycle`() {
        whenever(bannerRepo.save(any())).thenAnswer { it.getArgument<PromoBanner>(0) }
        val providerId = UUID.randomUUID()

        val saved = service.upsertBanner(
            PromoBanner(
                title = "Merchant bid",
                subtitle = "Pending auction",
                providerId = providerId,
            )
        )

        assertEquals("PENDING_BID", saved.status)
    }

    @Test
    fun `upsertGuide - merchant without writer grant - rejected`() {
        whenever(writerRepo.findByUserId(merchantId)).thenReturn(null)

        assertThrows<ContentAccessDeniedException> {
            service.upsertGuide(
                GuideArticle(
                    category = "skin",
                    title = "Test",
                    summary = "Summary",
                    authorUserId = merchantId,
                ),
                callerRole = "MERCHANT",
            )
        }
    }

    @Test
    fun `upsertGuide - granted active writer - uses approved attribution`() {
        whenever(writerRepo.findByUserId(merchantId)).thenReturn(
            GuideWriter(
                userId = merchantId,
                email = "vet@example.com",
                authorName = "Dr. Ananya Rao",
                companyName = "City Pet Hospital",
                accessStatus = "ACTIVE",
            ),
        )
        whenever(guideRepo.save(any())).thenAnswer { it.getArgument<GuideArticle>(0) }

        val saved = service.upsertGuide(
            GuideArticle(
                category = "skin",
                title = "Test",
                summary = "Summary",
                authorUserId = merchantId,
            ),
            callerRole = "MERCHANT",
        )

        assertEquals("Dr. Ananya Rao", saved.authorName)
        assertEquals("City Pet Hospital", saved.companyName)
    }

    @Test
    fun `toggleGuideLike - adds and removes one like per user`() {
        val articleId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val article = GuideArticle(
            articleId = articleId,
            category = "skin",
            title = "Test",
            summary = "Summary",
            likeCount = 0,
        )
        whenever(guideRepo.findById(articleId)).thenReturn(Optional.of(article))
        whenever(guideRepo.save(any())).thenAnswer { it.getArgument<GuideArticle>(0) }
        whenever(likeRepo.findByArticleIdAndUserId(articleId, userId)).thenReturn(null)

        val result = service.toggleGuideLike(articleId, userId)

        assertTrue(result.liked)
        assertEquals(1L, result.likeCount)
        verify(likeRepo).save(any<GuideLike>())
    }
}
