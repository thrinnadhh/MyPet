package com.pawsnearme.common.module

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Transport-neutral module contracts used by the modular-monolith runtime.
 *
 * The distributed services bind these ports to HTTP adapters. The consolidated
 * application binds them to direct module facades. Callers therefore depend on
 * stable business capabilities rather than REST paths, headers, or gRPC stubs.
 */
interface CatalogModuleApi {
    fun offering(offeringId: UUID): CatalogOfferingSnapshot
    fun reserveStock(command: StockMutationCommand): CatalogOfferingSnapshot
    fun restoreStock(command: StockMutationCommand): CatalogOfferingSnapshot
    fun slot(slotId: UUID): CatalogSlotSnapshot?
    fun updateSlotStatus(slotId: UUID, status: String): CatalogSlotSnapshot
}

interface ProviderModuleApi {
    fun ownerUserId(providerId: UUID): UUID?
    fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot>
}

interface PaymentModuleApi {
    fun transaction(transactionId: UUID): PaymentTransactionSnapshot?
    fun promotionTerms(
        code: String,
        orderValue: BigDecimal,
        providerId: UUID,
        category: String? = null
    ): PromotionTerms

    fun reserveCoupon(command: CouponReservationCommand): BigDecimal
    fun releaseCoupon(code: String, userId: UUID, orderId: UUID)
    fun redeemCoupon(code: String, userId: UUID, orderId: UUID)
    fun codEligibility(amount: BigDecimal, city: String?, providerId: UUID?): CodEligibilityDecision
    fun refundOrder(orderId: UUID)
    fun recordOrderDelivered(orderId: UUID, customerId: UUID, providerId: UUID, netAmount: BigDecimal)
    fun recordOrderRefunded(orderId: UUID, customerId: UUID, providerId: UUID)
}

interface DiscoveryModuleApi {
    fun checkServiceability(
        city: String?,
        latitude: Double?,
        longitude: Double?,
        pincode: String? = null
    ): ServiceabilityDecision
}

interface OrderModuleApi {
    fun updateStatus(orderId: UUID, status: String, actorId: UUID, note: String? = null)
}

data class CatalogOfferingSnapshot(
    val offeringId: UUID,
    val providerId: UUID,
    val name: String,
    val price: BigDecimal,
    val status: String,
    val stockQuantity: Int?
)

data class StockMutationCommand(
    val offeringId: UUID,
    val quantity: Int,
    val idempotencyKey: UUID
)

data class CatalogSlotSnapshot(
    val slotId: UUID,
    val slotStart: Instant?,
    val slotEnd: Instant?,
    val status: String
)

data class VaccinationReminderSnapshot(
    val reminderId: UUID,
    val ownerId: UUID,
    val petId: UUID,
    val vaccineName: String,
    val dueDate: LocalDate,
    val enabled: Boolean
)

data class PaymentTransactionSnapshot(
    val transactionId: UUID,
    val userId: UUID,
    val referenceId: UUID,
    val transactionType: String,
    val amount: BigDecimal,
    val status: String
)

data class PromotionTerms(
    val discountType: String,
    val discountValue: BigDecimal,
    val maxDiscountAmount: BigDecimal?
)

data class CouponReservationCommand(
    val code: String,
    val orderValue: BigDecimal,
    val providerId: UUID,
    val userId: UUID,
    val orderId: UUID,
    val category: String? = null
)

data class CodEligibilityDecision(
    val eligible: Boolean,
    val maxAllowedAmount: BigDecimal?,
    val reason: String?
)

data class ServiceabilityDecision(
    val serviceable: Boolean,
    val reason: String?
)
