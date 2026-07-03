package com.pawsnearme.orderservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderStatus {
    PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP,
    ASSIGNED, REASSIGNED, PICKED_UP, DELIVERED, COMPLETED,
    REJECTED, CANCELLED
}

@Entity
@Table(name = "orders", schema = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_id")
    var orderId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "captain_id")
    var captainId: UUID? = null,

    @Column(name = "delivery_address_id", nullable = false)
    var deliveryAddressId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = OrderStatus.PLACED,

    @Column(name = "subtotal_amount", nullable = false)
    var subtotalAmount: BigDecimal,

    @Column(name = "delivery_fee", nullable = false)
    var deliveryFee: BigDecimal = BigDecimal.ZERO,

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: BigDecimal,

    @Column(name = "payment_id")
    var paymentId: UUID? = null,

    @Column(name = "placed_at", nullable = false)
    var placedAt: Instant = Instant.now(),

    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null,

    @Column(name = "ready_at")
    var readyAt: Instant? = null,

    @Column(name = "picked_up_at")
    var picked_upAt: Instant? = null,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "cancellation_reason")
    var cancellationReason: String? = null
)

@Entity
@Table(name = "order_items", schema = "orders")
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_item_id")
    var orderItemId: UUID? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "offering_name_snapshot", nullable = false)
    var offeringNameSnapshot: String,

    @Column(name = "unit_price_snapshot", nullable = false)
    var unitPriceSnapshot: BigDecimal,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "line_total", nullable = false)
    var lineTotal: BigDecimal
)

@Entity
@Table(name = "order_status_history", schema = "orders")
class OrderStatusHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "history_id")
    var historyId: UUID? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    var fromStatus: OrderStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    var toStatus: OrderStatus,

    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now(),

    @Column(name = "changed_by_user_id")
    var changedByUserId: UUID? = null,

    @Column(name = "note")
    var note: String? = null
)

@Entity
@Table(name = "system_configs", schema = "orders")
class SystemConfig(
    @Id
    @Column(name = "config_key")
    var configKey: String,

    @Column(name = "config_value", nullable = false)
    var configValue: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "disputes", schema = "orders")
class Dispute(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "dispute_id")
    var disputeId: UUID? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "status", nullable = false)
    var status: String = "OPEN", // OPEN, RESOLVED, REJECTED

    @Column(name = "reason", nullable = false)
    var reason: String,

    @Column(name = "resolution_notes")
    var resolutionNotes: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
)

@Entity
@Table(name = "support_cases", schema = "orders")
class SupportCase(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "support_case_id")
    var supportCaseId: UUID? = null,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "detail", nullable = false)
    var detail: String,

    @Column(name = "action_type", nullable = false)
    var actionType: String,

    @Column(name = "entity_type")
    var entityType: String? = null,

    @Column(name = "entity_id")
    var entityId: UUID? = null,

    @Column(name = "status", nullable = false)
    var status: String = "OPEN",

    @Column(name = "created_by_user_id")
    var createdByUserId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "resolution_notes")
    var resolutionNotes: String? = null
)

@Entity
@Table(name = "invoices", schema = "orders")
class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "invoice_id")
    var invoiceId: UUID? = null,

    @Column(name = "order_id", nullable = false, unique = true)
    var orderId: UUID,

    @Column(name = "invoice_number", nullable = false, unique = true)
    var invoiceNumber: String,

    @Column(name = "subtotal_amount", nullable = false)
    var subtotalAmount: BigDecimal,

    @Column(name = "tax_amount", nullable = false)
    var taxAmount: BigDecimal,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: BigDecimal,

    @Column(name = "generated_at", nullable = false)
    var generatedAt: Instant = Instant.now()
)
