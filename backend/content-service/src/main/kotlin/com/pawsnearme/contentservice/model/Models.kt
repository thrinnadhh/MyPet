package com.pawsnearme.contentservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "promo_banners", schema = "content")
class PromoBanner(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "banner_id")
    var bannerId: UUID? = null,
    @Column(name = "title", nullable = false) var title: String,
    @Column(name = "subtitle", nullable = false) var subtitle: String,
    @Column(name = "accent_color", nullable = false) var accentColor: String = "#F97316",
    @Column(name = "duration_sec", nullable = false) var durationSec: Int = 5,
    @Column(name = "sort_order", nullable = false) var sortOrder: Int = 0,
    @Column(name = "active", nullable = false) var active: Boolean = true,
    @Column(name = "provider_id") var providerId: UUID? = null,
    @Column(name = "bid_amount") var bidAmount: java.math.BigDecimal? = null,
    @Column(name = "status", nullable = false) var status: String = "PENDING_BID",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "banner_bids", schema = "content")
class BannerBid(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "bid_id")
    var bidId: UUID? = null,
    @Column(name = "provider_id", nullable = false) var providerId: UUID,
    @Column(name = "owner_user_id", nullable = false) var ownerUserId: UUID,
    @Column(name = "slot_order", nullable = false) var slotOrder: Int,
    @Column(name = "bid_amount", nullable = false) var bidAmount: java.math.BigDecimal,
    @Column(name = "window_ends_at", nullable = false) var windowEndsAt: Instant,
    @Column(name = "status", nullable = false) var status: String = "PENDING",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "guide_articles", schema = "content")
class GuideArticle(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "article_id")
    var articleId: UUID? = null,
    @Column(name = "category", nullable = false) var category: String,
    @Column(name = "title", nullable = false) var title: String,
    @Column(name = "summary", nullable = false) var summary: String,
    @Column(name = "body") var body: String? = null,
    @Column(name = "read_minutes", nullable = false) var readMinutes: Int = 3,
    @Column(name = "published", nullable = false) var published: Boolean = true,
    @Column(name = "author_user_id") var authorUserId: UUID? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "guide_writers", schema = "content")
class GuideWriter(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "writer_id")
    var writerId: UUID? = null,
    @Column(name = "user_id", nullable = false, unique = true) var userId: UUID,
    @Column(name = "email", nullable = false) var email: String,
    @Column(name = "access_status", nullable = false) var accessStatus: String = "ACTIVE",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)
