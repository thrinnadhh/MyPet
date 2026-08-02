package com.pawsnearme.orderservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "customer_cases", schema = "orders")
class CustomerCase(
    @Id
    @Column(name = "case_id", nullable = false)
    var caseId: UUID = UUID.randomUUID(),

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "case_type", nullable = false, length = 60)
    var caseType: String,

    @Column(name = "description", nullable = false, length = 2000)
    var description: String,

    @Column(name = "status", nullable = false, length = 40)
    var status: String = "OPEN",

    @Column(name = "refund_status", nullable = false, length = 40)
    var refundStatus: String = "NOT_APPLICABLE",

    @Column(name = "resolution_notes", length = 2000)
    var resolutionNotes: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
)

@Entity
@Table(name = "customer_case_evidence", schema = "orders")
class CustomerCaseEvidence(
    @Id
    @Column(name = "evidence_id", nullable = false)
    var evidenceId: UUID = UUID.randomUUID(),

    @Column(name = "case_id", nullable = false)
    var caseId: UUID,

    @Column(name = "uploader_user_id", nullable = false)
    var uploaderUserId: UUID,

    @Column(name = "original_filename", nullable = false, length = 255)
    var originalFilename: String,

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    var storageKey: String,

    @Column(name = "mime_type", nullable = false, length = 100)
    var mimeType: String,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
