package com.pawsnearme.paymentservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "loyalty_audit_logs", schema = "payments")
class LoyaltyAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "audit_id")
    var auditId: UUID? = null,

    @Column(name = "actor_id", nullable = false)
    var actorId: UUID,

    @Column(name = "provider_id")
    var providerId: UUID? = null,

    @Column(name = "action", nullable = false)
    var action: String,

    @Column(name = "before_json", columnDefinition = "TEXT")
    var beforeJson: String? = null,

    @Column(name = "after_json", columnDefinition = "TEXT")
    var afterJson: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
