package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.RecurringOrderOccurrenceStatus
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.repository.RecurringOrderOccurrenceRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionItemRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AdminRecurringItemView(
    val offeringId: UUID,
    val offeringName: String,
    val baseQuantity: Int,
    val unitPriceAtCreation: BigDecimal
)

data class AdminRecurringOccurrenceView(
    val occurrenceId: UUID,
    val scheduledFor: Instant,
    val orderId: UUID?,
    val status: RecurringOrderOccurrenceStatus,
    val failureCode: String?,
    val failureDetail: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class AdminRecurringSubscriptionView(
    val subscriptionId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val sourceOrderId: UUID,
    val deliveryAddressId: UUID,
    val cadenceDays: Int,
    val quantityMultiplier: Int,
    val paymentMethod: String,
    val status: RecurringOrderStatus,
    val nextOrderAt: Instant,
    val lastExecutedAt: Instant?,
    val lastOrderId: UUID?,
    val lastFailureCode: String?,
    val lastFailureDetail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<AdminRecurringItemView> = emptyList()
)

data class AdminRecurringSubscriptionPage(
    val content: List<AdminRecurringSubscriptionView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class AdminRecurringTraceView(
    val subscription: AdminRecurringSubscriptionView,
    val occurrences: List<AdminRecurringOccurrenceView>,
    val occurrencePage: Int,
    val occurrenceSize: Int,
    val occurrenceTotalElements: Long,
    val occurrenceTotalPages: Int
)

@Service
class RecurringOrderAdminService(
    private val subscriptionRepository: RecurringOrderSubscriptionRepository,
    private val itemRepository: RecurringOrderSubscriptionItemRepository,
    private val occurrenceRepository: RecurringOrderOccurrenceRepository
) {
    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): AdminRecurringSubscriptionPage {
        requirePage(page, size)
        val result = subscriptionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return AdminRecurringSubscriptionPage(
            content = result.content.map { subscription ->
                val items = itemRepository.findBySubscriptionIdOrderByCreatedAtAsc(subscription.subscriptionId)
                AdminRecurringSubscriptionView(
                    subscriptionId = subscription.subscriptionId,
                    customerId = subscription.customerId,
                    providerId = subscription.providerId,
                    sourceOrderId = subscription.sourceOrderId,
                    deliveryAddressId = subscription.deliveryAddressId,
                    cadenceDays = subscription.cadenceDays,
                    quantityMultiplier = subscription.quantityMultiplier,
                    paymentMethod = subscription.paymentMethod,
                    status = subscription.status,
                    nextOrderAt = subscription.nextOrderAt,
                    lastExecutedAt = subscription.lastExecutedAt,
                    lastOrderId = subscription.lastOrderId,
                    lastFailureCode = subscription.lastFailureCode,
                    lastFailureDetail = subscription.lastFailureDetail,
                    createdAt = subscription.createdAt,
                    updatedAt = subscription.updatedAt,
                    items = items.map {
                        AdminRecurringItemView(
                            offeringId = it.offeringId,
                            offeringName = it.offeringNameSnapshot,
                            baseQuantity = it.baseQuantity,
                            unitPriceAtCreation = it.unitPriceAtCreation
                        )
                    }
                )
            },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun trace(subscriptionId: UUID, page: Int, size: Int): AdminRecurringTraceView {
        requirePage(page, size)
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow { NoSuchElementException("Recurring subscription not found: $subscriptionId") }
        val items = itemRepository.findBySubscriptionIdOrderByCreatedAtAsc(subscriptionId)
        val occurrences = occurrenceRepository.findBySubscriptionIdOrderByScheduledForDesc(
            subscriptionId,
            PageRequest.of(page, size)
        )
        val subscriptionView = AdminRecurringSubscriptionView(
            subscriptionId = subscription.subscriptionId,
            customerId = subscription.customerId,
            providerId = subscription.providerId,
            sourceOrderId = subscription.sourceOrderId,
            deliveryAddressId = subscription.deliveryAddressId,
            cadenceDays = subscription.cadenceDays,
            quantityMultiplier = subscription.quantityMultiplier,
            paymentMethod = subscription.paymentMethod,
            status = subscription.status,
            nextOrderAt = subscription.nextOrderAt,
            lastExecutedAt = subscription.lastExecutedAt,
            lastOrderId = subscription.lastOrderId,
            lastFailureCode = subscription.lastFailureCode,
            lastFailureDetail = subscription.lastFailureDetail,
            createdAt = subscription.createdAt,
            updatedAt = subscription.updatedAt,
            items = items.map {
                AdminRecurringItemView(
                    offeringId = it.offeringId,
                    offeringName = it.offeringNameSnapshot,
                    baseQuantity = it.baseQuantity,
                    unitPriceAtCreation = it.unitPriceAtCreation
                )
            }
        )
        return AdminRecurringTraceView(
            subscription = subscriptionView,
            occurrences = occurrences.content.map {
                AdminRecurringOccurrenceView(
                    occurrenceId = it.occurrenceId,
                    scheduledFor = it.scheduledFor,
                    orderId = it.orderId,
                    status = it.status,
                    failureCode = it.failureCode,
                    failureDetail = it.failureDetail,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            occurrencePage = occurrences.number,
            occurrenceSize = occurrences.size,
            occurrenceTotalElements = occurrences.totalElements,
            occurrenceTotalPages = occurrences.totalPages
        )
    }

    private fun requirePage(page: Int, size: Int) {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
    }
}