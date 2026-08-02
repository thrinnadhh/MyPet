package com.pawsnearme.appointmentservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "medical_documents", schema = "appointments")
class MedicalDocument(
    @Id
    @Column(name = "document_id", nullable = false)
    var documentId: UUID = UUID.randomUUID(),

    @Column(name = "appointment_id", nullable = false)
    var appointmentId: UUID,

    @Column(name = "owner_user_id", nullable = false)
    var ownerUserId: UUID,

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

    @Column(name = "status", nullable = false, length = 40)
    var status: String = "AVAILABLE",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "medical_document_access_logs", schema = "appointments")
class MedicalDocumentAccessLog(
    @Id
    @Column(name = "access_id", nullable = false)
    var accessId: UUID = UUID.randomUUID(),

    @Column(name = "document_id", nullable = false)
    var documentId: UUID,

    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID,

    @Column(name = "action", nullable = false, length = 40)
    var action: String,

    @Column(name = "trace_id", nullable = false, length = 160)
    var traceId: String,

    @Column(name = "accessed_at", nullable = false)
    var accessedAt: Instant = Instant.now()
)
