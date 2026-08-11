package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "featured_collections", schema = "catalog")
class FeaturedCollection(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "collection_id")
    var collectionId: UUID? = null,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "slug", nullable = false, unique = true)
    var slug: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
