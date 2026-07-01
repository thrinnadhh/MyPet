package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bills", schema = "billing")
class Bill(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    var id: UUID? = null,

    @Column(name = "store_id", nullable = false)
    var storeId: UUID,

    @Column(name = "staff_id", nullable = false)
    var staffId: UUID,

    @Column(name = "status", nullable = false)
    var status: String, // DRAFT, FINALIZED, SYNCED

    @Column(name = "subtotal", nullable = false)
    var subtotal: BigDecimal,

    @Column(name = "total_discount", nullable = false)
    var totalDiscount: BigDecimal,

    @Column(name = "tax", nullable = false)
    var tax: BigDecimal,

    @Column(name = "grand_total", nullable = false)
    var grandTotal: BigDecimal,

    @Column(name = "idempotency_key", nullable = false, unique = true)
    var idempotencyKey: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "synced_at")
    var syncedAt: Instant? = null
)

@Entity
@Table(name = "bill_items", schema = "billing")
class BillItem(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    var id: UUID? = null,

    @Column(name = "bill_id", nullable = false)
    var billId: UUID,

    @Column(name = "product_id", nullable = false)
    var productId: UUID,

    @Column(name = "barcode_scanned", nullable = false)
    var barcodeScanned: String,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "unit_price", nullable = false)
    var unitPrice: BigDecimal,

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: BigDecimal,

    @Column(name = "discount_type", nullable = false)
    var discountType: String // PERCENT, FLAT, NONE
)
