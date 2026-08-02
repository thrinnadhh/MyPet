package com.pawsnearme.orderservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admin_audit_logs", schema = "orders")
class AdminAuditLog(
    @Id
    @Column(name = "audit_id", nullable = false)
    var auditId: UUID = UUID.randomUUID(),

    @Column(name = "admin_user_id", nullable = false)
    var adminUserId: UUID,

    @Column(name = "action", nullable = false, length = 120)
    var action: String,

    @Column(name = "entity_type", nullable = false, length = 80)
    var entityType: String,

    @Column(name = "entity_id", length = 160)
    var entityId: String? = null,

    @Column(name = "previous_value", columnDefinition = "TEXT")
    var previousValue: String? = null,

    @Column(name = "new_value", columnDefinition = "TEXT")
    var newValue: String? = null,

    @Column(name = "reason", nullable = false, length = 500)
    var reason: String,

    @Column(name = "trace_id", nullable = false, length = 160)
    var traceId: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "service_area_configs", schema = "orders")
class ServiceAreaConfig(
    @Id
    @Column(name = "pincode", nullable = false, length = 6)
    var pincode: String,

    @Column(name = "city", nullable = false, length = 120)
    var city: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "delivery_enabled", nullable = false)
    var deliveryEnabled: Boolean = true,

    @Column(name = "service_radius_km", nullable = false, precision = 6, scale = 2)
    var serviceRadiusKm: BigDecimal,

    @Column(name = "emergency_message", length = 500)
    var emergencyMessage: String? = null,

    @Column(name = "updated_by_user_id", nullable = false)
    var updatedByUserId: UUID,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
