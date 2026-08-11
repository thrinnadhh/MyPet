package com.pawsnearme.catalogservice.dto

import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.model.SlotStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OfferingRequest(
    @field:NotNull(message = "Provider ID is required")
    val providerId: UUID?,

    @field:NotBlank(message = "Offering name is required")
    val name: String?,

    val description: String?,

    val category: String?,

    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Price must be non-negative")
    val price: BigDecimal?,

    val imageUrl: String?,

    val status: OfferingStatus = OfferingStatus.ACTIVE,

    @field:Min(value = 0, message = "Stock quantity must be non-negative")
    val stockQuantity: Int?,

    val sku: String?,

    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int?,

    @field:Size(max = 50, message = "Barcode cannot exceed 50 characters")
    val barcode: String? = null,

    @field:DecimalMin(value = "0.0", inclusive = true, message = "GST rate must be non-negative")
    val gstRate: BigDecimal? = null
)

data class SlotRequest(
    @field:NotNull(message = "Offering ID is required")
    val offeringId: UUID?,

    @field:NotNull(message = "Slot start time is required")
    val slotStart: Instant?,

    @field:NotNull(message = "Slot end time is required")
    val slotEnd: Instant?,

    val status: SlotStatus = SlotStatus.AVAILABLE
)

data class BillItemRequest(
    @field:NotNull(message = "Product ID is required")
    val productId: UUID?,

    @field:NotBlank(message = "Barcode is required")
    @field:Size(max = 50, message = "Barcode cannot exceed 50 characters")
    val barcodeScanned: String?,

    @field:NotNull(message = "Quantity is required")
    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int?,

    @field:NotNull(message = "Unit price is required")
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Unit price must be non-negative")
    val unitPrice: BigDecimal?,

    @field:NotNull(message = "Discount amount is required")
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Discount amount must be non-negative")
    val discountAmount: BigDecimal?,

    @field:NotBlank(message = "Discount type is required")
    @field:Pattern(regexp = "NONE|FLAT|PERCENT", message = "Discount type must be NONE, FLAT or PERCENT")
    val discountType: String?
)

data class BillRequest(
    @field:NotNull(message = "Store ID is required")
    val storeId: UUID?,

    @field:NotNull(message = "Staff ID is required")
    val staffId: UUID?,

    @field:NotBlank(message = "Status is required")
    val status: String?,

    @field:NotNull(message = "Subtotal is required")
    val subtotal: BigDecimal?,

    @field:NotNull(message = "Total discount is required")
    val totalDiscount: BigDecimal?,

    @field:NotNull(message = "Tax is required")
    val tax: BigDecimal?,

    @field:NotNull(message = "Grand total is required")
    val grandTotal: BigDecimal?,

    @field:NotBlank(message = "Idempotency key is required")
    @field:Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
    val idempotencyKey: String?,

    @field:NotEmpty(message = "At least one bill item is required")
    @field:Valid
    val items: List<BillItemRequest> = emptyList()
)

data class FailedBillItem(
    val productId: UUID?,
    val barcode: String,
    val reason: String
)

data class BillResponse(
    val bill: com.pawsnearme.catalogservice.model.Bill,
    val successfulItems: List<com.pawsnearme.catalogservice.model.BillItem>,
    val failedItems: List<FailedBillItem>
)
