package com.pawsnearme.providerservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "provider_documents", schema = "providers")
class ProviderDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "document_id")
    var documentId: UUID? = null,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "doc_type", nullable = false)
    var docType: String,

    @Column(name = "doc_url", nullable = false)
    var docUrl: String,

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    var uploadedAt: Instant = Instant.now(),

    @Column(name = "reviewed", nullable = false)
    var reviewed: Boolean = false,

    @Column(name = "review_note")
    var reviewNote: String? = null
)
