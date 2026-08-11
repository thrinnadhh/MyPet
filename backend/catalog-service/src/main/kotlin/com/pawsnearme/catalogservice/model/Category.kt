package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "categories", schema = "catalog")
class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "category_id")
    var categoryId: UUID? = null,

    @Column(name = "slug", nullable = false, unique = true)
    var slug: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "pet_type", nullable = false)
    var petType: String = "ALL", // 'DOG', 'CAT', 'BIRD', 'FISH', 'SMALL_ANIMAL', 'ALL'

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
