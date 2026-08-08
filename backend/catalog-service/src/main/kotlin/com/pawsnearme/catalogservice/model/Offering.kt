package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "offerings", schema = "catalog")
class Offering(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "offering_id")
    var offeringId: UUID? = null,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "category")
    var category: String? = null,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OfferingStatus = OfferingStatus.ACTIVE,

    @Column(name = "stock_quantity")
    var stockQuantity: Int? = null,

    @Column(name = "sku")
    var sku: String? = null,

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,

    @Column(name = "barcode")
    var barcode: String? = null,

    @Column(name = "admin_disabled", nullable = false)
    var adminDisabled: Boolean = false,

    @Column(name = "moderation_reason", length = 500)
    var moderationReason: String? = null,

    @Column(name = "moderated_by_user_id")
    var moderatedByUserId: UUID? = null,

    @Column(name = "moderated_at")
    var moderatedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "moderation_audit_logs", schema = "catalog")
class CatalogModerationAuditLog(
    @Id
    @Column(name = "audit_id")
    var auditId: UUID = UUID.randomUUID(),

    @Column(name = "admin_user_id", nullable = false)
    var adminUserId: UUID,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "action", nullable = false, length = 80)
    var action: String,

    @Column(name = "previous_status", nullable = false, length = 32)
    var previousStatus: String,

    @Column(name = "new_status", nullable = false, length = 32)
    var newStatus: String,

    @Column(name = "reason", nullable = false, length = 500)
    var reason: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
