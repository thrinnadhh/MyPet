package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderOccurrence
import com.pawsnearme.orderservice.model.RecurringOrderOccurrenceStatus
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.RecurringOrderOccurrenceRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CreateRecurringOrderRequest(
    val sourceOrderId: UUID,
    val cadenceDays: Int,
    val deliveryAddressId: UUID? = null,
    val quantityMultiplier: Int = 1
)

data class UpdateRecurringOrderRequest(
    val action: String,
    val cadenceDays: Int? = null,
    val deliveryAddressId: UUID? = null,
    val quantityMultiplier: Int? = null
)

data class RecurringOrderOccurrenceView(
    val occurrenceId: UUID,
    val subscriptionId: UUID,
    val scheduledFor: Instant,
    val orderId: UUID?,
    val status: RecurringOrderOccurrenceStatus,
    val failureCode: String?,
    val failureDetail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RecurringOrderView(
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
    val lastRemindedAt: Instant?,
    val lastExecutedAt: Instant?,
    val lastOrderId: UUID?,
    val lastFailureCode: String?,
    val lastFailureDetail: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RecurringOrderConfirmation(
    val subscription: RecurringOrderView,
    val reorder: ReorderValidationResponse
)

data class RecurringOrderProcessingResult(
    val scanned: Int,
    val ordersCreated: Int,
    val failed: Int,
    val skipped: Int,
)

@Service
class RecurringOrderService(
    private val repository: RecurringOrderSubscriptionRepository,
    private val occurrenceRepository: RecurringOrderOccurrenceRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderService: OrderService,
    private val providerModule: ProviderModuleApi,
    private val discoveryModule: DiscoveryModuleApi,
    private val outboxService: OutboxService
) {
    @Transactional
    fun create(customerId: UUID, request: CreateRecurringOrderRequest): RecurringOrderView {
        validateCadence(request.cadenceDays)
        validateQuantity(request.quantityMultiplier)
        val order = orderRepository.findById(request.sourceOrderId)
            .orElseThrow { IllegalArgumentException("Source order not found.") }
        if (order.customerId != customerId) throw OrderAccessDeniedException("Source order belongs to another customer.")
        if (order.status !in setOf(OrderStatus.DELIVERED, OrderStatus.COMPLETED)) {
            throw IllegalArgumentException("Only delivered or completed orders can become recurring orders.")
        }
        if (repository.existsByCustomerIdAndSourceOrderIdAndStatusNot(customerId, request.sourceOrderId, RecurringOrderStatus.CANCELLED)) {
            throw IllegalStateException("An active subscription already exists for this order.")
        }
        val addressId = request.deliveryAddressId ?: order.deliveryAddressId
        requireNotNull(providerModule.deliveryAddress(customerId, addressId)) {
            "Selected recurring delivery address is missing or does not belong to the customer."
        }
        require(providerModule.providerOperational(order.providerId)) {
            "Merchant is not currently operational."
        }
        val paymentMethod = order.paymentMethod.trim().uppercase().takeIf { it in setOf("COD", "CARD", "UPI") } ?: "COD"
        val saved = repository.save(
            RecurringOrderSubscription(
                customerId = customerId,
                providerId = order.providerId,
                sourceOrderId = request.sourceOrderId,
                deliveryAddressId = addressId,
                cadenceDays = request.cadenceDays,
                quantityMultiplier = request.quantityMultiplier,
                paymentMethod = paymentMethod,
                nextOrderAt = Instant.now().plus(request.cadenceDays.toLong(), ChronoUnit.DAYS)
            )
        )
        publish("RecurringOrderCreated", saved, mapOf("paymentMethod" to paymentMethod))
        return saved.toView()
    }

    @Transactional(readOnly = true)
    fun list(customerId: UUID): List<RecurringOrderView> =
        repository.findByCustomerIdOrderByCreatedAtDesc(customerId).map { it.toView() }

    @Transactional(readOnly = true)
    fun listForProvider(providerId: UUID, actorId: UUID, actorRole: String?): List<RecurringOrderView> {
        if (actorRole != "ADMIN" && (actorRole != "MERCHANT" || providerModule.ownerUserId(providerId) != actorId)) {
            throw OrderAccessDeniedException("Merchant does not own this provider subscription view.")
        }
        return repository.findByProviderIdOrderByNextOrderAtAsc(providerId).map { it.toView() }
    }

    @Transactional(readOnly = true)
    fun occurrences(customerId: UUID, subscriptionId: UUID): List<RecurringOrderOccurrenceView> {
        owned(customerId, subscriptionId)
        return occurrenceRepository.findBySubscriptionIdOrderByScheduledForDesc(subscriptionId).map { it.toView() }
    }

    @Transactional
    fun update(customerId: UUID, subscriptionId: UUID, request: UpdateRecurringOrderRequest): RecurringOrderView {
        val subscription = ownedForUpdate(customerId, subscriptionId)
        val now = Instant.now()
        when (request.action.trim().uppercase()) {
            "PAUSE" -> {
                require(subscription.status != RecurringOrderStatus.CANCELLED) { "Cancelled subscriptions cannot be paused." }
                subscription.status = RecurringOrderStatus.PAUSED
            }
            "RESUME" -> {
                require(subscription.status == RecurringOrderStatus.PAUSED || subscription.status == RecurringOrderStatus.AWAITING_CONFIRMATION) {
                    "Only paused subscriptions can be resumed."
                }
                subscription.status = RecurringOrderStatus.ACTIVE
                subscription.nextOrderAt = now.plus(subscription.cadenceDays.toLong(), ChronoUnit.DAYS)
            }
            "SKIP" -> {
                require(subscription.status != RecurringOrderStatus.CANCELLED) { "Cancelled subscriptions cannot be skipped." }
                subscription.status = RecurringOrderStatus.ACTIVE
                subscription.nextOrderAt = subscription.nextOrderAt.plus(subscription.cadenceDays.toLong(), ChronoUnit.DAYS)
                subscription.lastRemindedAt = null
            }
            "CANCEL" -> subscription.status = RecurringOrderStatus.CANCELLED
            "CHANGE" -> {
                require(subscription.status != RecurringOrderStatus.CANCELLED) { "Cancelled subscriptions cannot be changed." }
                request.cadenceDays?.let {
                    validateCadence(it)
                    subscription.cadenceDays = it
                }
                request.quantityMultiplier?.let {
                    validateQuantity(it)
                    subscription.quantityMultiplier = it
                }
                request.deliveryAddressId?.let {
                    requireNotNull(providerModule.deliveryAddress(customerId, it)) {
                        "Selected recurring delivery address is missing or does not belong to the customer."
                    }
                    subscription.deliveryAddressId = it
                }
            }
            else -> throw IllegalArgumentException("Unsupported subscription action.")
        }
        subscription.updatedAt = now
        val saved = repository.save(subscription)
        publish("RecurringOrderUpdated", saved, mapOf("action" to request.action.trim().uppercase()))
        return saved.toView()
    }

    /**
     * Backward-compatible recovery for legacy AWAITING_CONFIRMATION rows. New due
     * subscriptions are processed automatically by the scheduler. Confirmation
     * never charges a payment; it simply reactivates the legacy row for execution.
     */
    @Transactional
    fun confirm(customerId: UUID, subscriptionId: UUID): RecurringOrderConfirmation {
        val subscription = ownedForUpdate(customerId, subscriptionId)
        require(subscription.status == RecurringOrderStatus.AWAITING_CONFIRMATION) {
            "This subscription is not awaiting customer confirmation."
        }
        val validation = orderService.revalidateReorder(subscription.sourceOrderId, customerId, "CUSTOMER")
        if (!validation.canReorder) {
            return RecurringOrderConfirmation(subscription.toView(), validation)
        }
        subscription.status = RecurringOrderStatus.ACTIVE
        subscription.nextOrderAt = Instant.now()
        subscription.lastRemindedAt = null
        subscription.updatedAt = Instant.now()
        val saved = repository.save(subscription)
        publish("RecurringOrderReactivated", saved, mapOf("automaticCharge" to false))
        return RecurringOrderConfirmation(saved.toView(), validation)
    }

    @Transactional
    fun processDueOrders(now: Instant = Instant.now()): RecurringOrderProcessingResult {
        val dueIds = repository.findByStatusAndNextOrderAtLessThanEqual(RecurringOrderStatus.ACTIVE, now)
            .map { it.subscriptionId }
        var created = 0
        var failed = 0
        var skipped = 0

        dueIds.forEach { subscriptionId ->
            val subscription = repository.findByIdForUpdate(subscriptionId).orElse(null)
            if (subscription == null || subscription.status != RecurringOrderStatus.ACTIVE || subscription.nextOrderAt.isAfter(now)) {
                skipped += 1
                return@forEach
            }
            val scheduledFor = subscription.nextOrderAt
            val existing = occurrenceRepository.findBySubscriptionIdAndScheduledFor(subscriptionId, scheduledFor)
            if (existing != null) {
                if (existing.status == RecurringOrderOccurrenceStatus.ORDER_CREATED && subscription.nextOrderAt == scheduledFor) {
                    advanceAfterSuccess(subscription, existing.orderId, scheduledFor, now)
                }
                skipped += 1
                return@forEach
            }

            val occurrence = occurrenceRepository.save(
                RecurringOrderOccurrence(
                    subscriptionId = subscription.subscriptionId,
                    scheduledFor = scheduledFor,
                )
            )
            try {
                val order = generateOrder(subscription, occurrence)
                occurrence.orderId = requireNotNull(order.orderId)
                occurrence.status = RecurringOrderOccurrenceStatus.ORDER_CREATED
                occurrence.updatedAt = Instant.now()
                occurrenceRepository.save(occurrence)
                advanceAfterSuccess(subscription, occurrence.orderId, scheduledFor, now)
                publish(
                    "RecurringOrderGenerated",
                    subscription,
                    mapOf(
                        "occurrenceId" to occurrence.occurrenceId.toString(),
                        "scheduledFor" to scheduledFor.toString(),
                        "orderId" to occurrence.orderId.toString(),
                        "automaticCharge" to false,
                        "paymentMethod" to subscription.paymentMethod,
                    )
                )
                created += 1
            } catch (error: Exception) {
                val code = failureCode(error)
                occurrence.status = RecurringOrderOccurrenceStatus.FAILED
                occurrence.failureCode = code
                occurrence.failureDetail = error.message?.take(1000)
                occurrence.updatedAt = Instant.now()
                occurrenceRepository.save(occurrence)

                subscription.lastExecutedAt = now
                subscription.lastFailureCode = code
                subscription.lastFailureDetail = error.message?.take(1000)
                subscription.nextOrderAt = scheduledFor.plus(subscription.cadenceDays.toLong(), ChronoUnit.DAYS)
                subscription.updatedAt = now
                repository.save(subscription)
                publish(
                    "RecurringOrderFailed",
                    subscription,
                    mapOf(
                        "occurrenceId" to occurrence.occurrenceId.toString(),
                        "scheduledFor" to scheduledFor.toString(),
                        "failureCode" to code,
                        "failureDetail" to occurrence.failureDetail,
                    )
                )
                failed += 1
            }
        }
        return RecurringOrderProcessingResult(dueIds.size, created, failed, skipped)
    }

    private fun generateOrder(subscription: RecurringOrderSubscription, occurrence: RecurringOrderOccurrence) = run {
        require(providerModule.providerOperational(subscription.providerId)) {
            "MERCHANT_UNAVAILABLE: Merchant is not currently operational."
        }
        val address = requireNotNull(providerModule.deliveryAddress(subscription.customerId, subscription.deliveryAddressId)) {
            "ADDRESS_INVALID: Recurring delivery address is missing or no longer belongs to the customer."
        }
        val serviceability = discoveryModule.checkServiceability(
            city = address.city,
            latitude = address.latitude,
            longitude = address.longitude,
            pincode = address.pincode,
        )
        require(serviceability.serviceable) {
            "ADDRESS_UNSERVICEABLE: ${serviceability.reason ?: "Delivery address is outside the service area."}"
        }

        val sourceOrder = orderRepository.findById(subscription.sourceOrderId)
            .orElseThrow { IllegalStateException("SOURCE_ORDER_MISSING: Source order no longer exists.") }
        require(sourceOrder.providerId == subscription.providerId) { "PROVIDER_MISMATCH: Source order merchant changed unexpectedly." }
        val sourceItems = orderItemRepository.findByOrderId(subscription.sourceOrderId)
        require(sourceItems.isNotEmpty()) { "PRODUCT_UNAVAILABLE: Source order has no recurring items." }
        val items = sourceItems.map { item ->
            OrderItemRequest(
                offeringId = item.offeringId,
                quantity = Math.multiplyExact(item.quantity, subscription.quantityMultiplier),
            )
        }

        val quote = orderService.calculateQuote(
            CheckoutQuoteRequest(
                customerId = subscription.customerId,
                providerId = subscription.providerId,
                deliveryAddressId = subscription.deliveryAddressId,
                items = items,
                paymentMethod = subscription.paymentMethod,
                city = address.city,
                latitude = address.latitude,
                longitude = address.longitude,
            )
        )
        orderService.createOrder(
            CreateOrderRequest(
                customerId = subscription.customerId,
                providerId = subscription.providerId,
                deliveryAddressId = subscription.deliveryAddressId,
                items = items,
                paymentMethod = subscription.paymentMethod,
                quoteToken = quote.quoteToken,
                city = address.city,
                latitude = address.latitude,
                longitude = address.longitude,
            )
        )
    }

    private fun advanceAfterSuccess(
        subscription: RecurringOrderSubscription,
        orderId: UUID?,
        scheduledFor: Instant,
        now: Instant,
    ) {
        subscription.status = RecurringOrderStatus.ACTIVE
        subscription.lastExecutedAt = now
        subscription.lastOrderId = orderId
        subscription.lastFailureCode = null
        subscription.lastFailureDetail = null
        subscription.lastRemindedAt = null
        subscription.nextOrderAt = scheduledFor.plus(subscription.cadenceDays.toLong(), ChronoUnit.DAYS)
        subscription.updatedAt = now
        repository.save(subscription)
    }

    private fun failureCode(error: Exception): String {
        val message = error.message.orEmpty().uppercase()
        return when {
            "MERCHANT_UNAVAILABLE" in message -> "MERCHANT_UNAVAILABLE"
            "ADDRESS_" in message || "SERVICE" in message -> "ADDRESS_UNSERVICEABLE"
            "STOCK" in message || "INVENTORY" in message -> "OUT_OF_STOCK"
            "PRODUCT" in message || "OFFERING" in message -> "PRODUCT_UNAVAILABLE"
            "PRICE" in message || "QUOTE" in message -> "PRICE_CHANGED"
            "PAYMENT" in message || "ONLINE CHECKOUT" in message || "COD_NOT_ELIGIBLE" in message -> "PAYMENT_REQUIRED"
            else -> "ORDER_GENERATION_FAILED"
        }
    }

    private fun owned(customerId: UUID, subscriptionId: UUID): RecurringOrderSubscription {
        val subscription = repository.findById(subscriptionId)
            .orElseThrow { IllegalArgumentException("Subscription not found.") }
        if (subscription.customerId != customerId) throw OrderAccessDeniedException("Subscription belongs to another customer.")
        return subscription
    }

    private fun ownedForUpdate(customerId: UUID, subscriptionId: UUID): RecurringOrderSubscription {
        val subscription = repository.findByIdForUpdate(subscriptionId)
            .orElseThrow { IllegalArgumentException("Subscription not found.") }
        if (subscription.customerId != customerId) throw OrderAccessDeniedException("Subscription belongs to another customer.")
        return subscription
    }

    private fun validateCadence(cadenceDays: Int) {
        require(cadenceDays in ALLOWED_CADENCES) { "Cadence must be one of 7, 15, 25, 30 or 35 days." }
    }

    private fun validateQuantity(quantityMultiplier: Int) {
        require(quantityMultiplier in 1..20) { "Quantity multiplier must be between 1 and 20." }
    }

    private fun publish(eventType: String, subscription: RecurringOrderSubscription, extra: Map<String, Any?> = emptyMap()) {
        outboxService.saveEvent(
            eventId = UUID.randomUUID(),
            aggregateType = "RECURRING_ORDER",
            aggregateId = subscription.subscriptionId,
            eventType = eventType,
            eventPayload = mapOf(
                "eventType" to eventType,
                "subscriptionId" to subscription.subscriptionId.toString(),
                "customerId" to subscription.customerId.toString(),
                "providerId" to subscription.providerId.toString(),
                "sourceOrderId" to subscription.sourceOrderId.toString(),
                "status" to subscription.status.name,
                "occurredAt" to Instant.now().toString(),
                "data" to extra
            )
        )
    }

    private fun RecurringOrderSubscription.toView() = RecurringOrderView(
        subscriptionId = subscriptionId,
        customerId = customerId,
        providerId = providerId,
        sourceOrderId = sourceOrderId,
        deliveryAddressId = deliveryAddressId,
        cadenceDays = cadenceDays,
        quantityMultiplier = quantityMultiplier,
        paymentMethod = paymentMethod,
        status = status,
        nextOrderAt = nextOrderAt,
        lastRemindedAt = lastRemindedAt,
        lastExecutedAt = lastExecutedAt,
        lastOrderId = lastOrderId,
        lastFailureCode = lastFailureCode,
        lastFailureDetail = lastFailureDetail,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun RecurringOrderOccurrence.toView() = RecurringOrderOccurrenceView(
        occurrenceId = occurrenceId,
        subscriptionId = subscriptionId,
        scheduledFor = scheduledFor,
        orderId = orderId,
        status = status,
        failureCode = failureCode,
        failureDetail = failureDetail,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        val ALLOWED_CADENCES = setOf(7, 15, 25, 30, 35)
    }
}