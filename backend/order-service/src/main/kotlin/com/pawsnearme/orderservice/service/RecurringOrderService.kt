package com.pawsnearme.orderservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import com.pawsnearme.orderservice.repository.OrderRepository
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

data class RecurringOrderView(
    val subscriptionId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val sourceOrderId: UUID,
    val deliveryAddressId: UUID,
    val cadenceDays: Int,
    val quantityMultiplier: Int,
    val status: RecurringOrderStatus,
    val nextOrderAt: Instant,
    val lastRemindedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RecurringOrderConfirmation(
    val subscription: RecurringOrderView,
    val reorder: ReorderValidationResponse,
    val createdOrderId: UUID? = null,
)

@Service
class RecurringOrderService(
    private val repository: RecurringOrderSubscriptionRepository,
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val outboxService: OutboxService,
    private val checkoutIntegrityService: CheckoutIntegrityService,
    private val deliveryAddressLookup: CustomerDeliveryAddressLookup,
    private val deliveryContactLookup: DeliveryContactLookup,
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
        val deliveryAddressId = request.deliveryAddressId ?: order.deliveryAddressId
        requireNotNull(deliveryAddressLookup.forCustomerAddress(customerId, deliveryAddressId)) {
            "Recurring delivery address must belong to the customer and remain available."
        }
        val saved = repository.save(
            RecurringOrderSubscription(
                customerId = customerId,
                providerId = order.providerId,
                sourceOrderId = request.sourceOrderId,
                deliveryAddressId = deliveryAddressId,
                cadenceDays = request.cadenceDays,
                quantityMultiplier = request.quantityMultiplier,
                nextOrderAt = Instant.now().plus(request.cadenceDays.toLong(), ChronoUnit.DAYS)
            )
        )
        publish("RecurringOrderCreated", saved)
        return saved.toView()
    }

    @Transactional(readOnly = true)
    fun list(customerId: UUID): List<RecurringOrderView> =
        repository.findByCustomerIdOrderByCreatedAtDesc(customerId).map { it.toView() }

    @Transactional
    fun update(customerId: UUID, subscriptionId: UUID, request: UpdateRecurringOrderRequest): RecurringOrderView {
        val subscription = owned(customerId, subscriptionId)
        val now = Instant.now()
        when (request.action.trim().uppercase()) {
            "PAUSE" -> {
                require(subscription.status != RecurringOrderStatus.CANCELLED) { "Cancelled subscriptions cannot be paused." }
                subscription.status = RecurringOrderStatus.PAUSED
            }
            "RESUME" -> {
                require(subscription.status == RecurringOrderStatus.PAUSED) { "Only paused subscriptions can be resumed." }
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
                request.cadenceDays?.let {
                    validateCadence(it)
                    subscription.cadenceDays = it
                }
                request.quantityMultiplier?.let {
                    validateQuantity(it)
                    subscription.quantityMultiplier = it
                }
                request.deliveryAddressId?.let {
                    requireNotNull(deliveryAddressLookup.forCustomerAddress(customerId, it)) {
                        "Recurring delivery address must belong to the customer and remain available."
                    }
                    subscription.deliveryAddressId = it
                }
                require(subscription.status != RecurringOrderStatus.CANCELLED) { "Cancelled subscriptions cannot be changed." }
            }
            else -> throw IllegalArgumentException("Unsupported subscription action.")
        }
        subscription.updatedAt = now
        val saved = repository.save(subscription)
        publish("RecurringOrderUpdated", saved, mapOf("action" to request.action.trim().uppercase()))
        return saved.toView()
    }

    /**
     * Customer confirmation creates a fresh normal order through CheckoutIntegrityService.
     * The recurring subscription is only a scheduler; fulfillment always starts at PLACED.
     */
    @Transactional
    fun confirm(customerId: UUID, subscriptionId: UUID): RecurringOrderConfirmation {
        val subscription = owned(customerId, subscriptionId)
        require(subscription.status == RecurringOrderStatus.AWAITING_CONFIRMATION) {
            "This subscription is not awaiting customer confirmation."
        }
        val sourceOrder = orderRepository.findById(subscription.sourceOrderId)
            .orElseThrow { IllegalArgumentException("Source order not found.") }
        require(sourceOrder.customerId == customerId) { "Source order belongs to another customer." }
        require(sourceOrder.providerId == subscription.providerId) { "Recurring provider no longer matches source order." }

        val validation = orderService.revalidateReorder(subscription.sourceOrderId, customerId, "CUSTOMER")
        if (!validation.canReorder) {
            return RecurringOrderConfirmation(subscription.toView(), validation)
        }
        val address = deliveryAddressLookup.forCustomerAddress(customerId, subscription.deliveryAddressId)
            ?: return RecurringOrderConfirmation(
                subscription.toView(),
                validation.copy(isProviderServiceable = false, canReorder = false),
            )
        val items = validation.items.map { item ->
            OrderItemRequest(
                offeringId = item.offeringId,
                quantity = item.quantity * subscription.quantityMultiplier,
            )
        }
        if (items.isEmpty()) {
            return RecurringOrderConfirmation(subscription.toView(), validation.copy(canReorder = false))
        }

        val quote = try {
            checkoutIntegrityService.calculateQuote(
                CheckoutQuoteRequest(
                    customerId = customerId,
                    providerId = subscription.providerId,
                    deliveryAddressId = subscription.deliveryAddressId,
                    items = items,
                    paymentMethod = sourceOrder.paymentMethod,
                    city = address.city,
                    latitude = address.latitude,
                    longitude = address.longitude,
                )
            )
        } catch (error: IllegalArgumentException) {
            return RecurringOrderConfirmation(
                subscription.toView(),
                validation.copy(isProviderServiceable = false, canReorder = false),
            )
        }

        val idempotencyKey = "recurring:${subscription.subscriptionId}:${subscription.nextOrderAt}"
        val created = checkoutIntegrityService.createOrder(
            CreateOrderRequest(
                customerId = customerId,
                providerId = subscription.providerId,
                deliveryAddressId = subscription.deliveryAddressId,
                items = items,
                paymentMethod = sourceOrder.paymentMethod,
                quoteToken = quote.quoteToken,
                city = address.city,
                latitude = address.latitude,
                longitude = address.longitude,
            ),
            idempotencyKey,
        )
        require(created.status == OrderStatus.PLACED) { "Recurring confirmation must create a normal PLACED order." }
        val contact = deliveryContactLookup.forCustomerAddress(customerId, subscription.deliveryAddressId)
        created.deliveryContactPhone = contact?.phoneNumber
        created.deliveryContactVerified = contact?.verified ?: false
        orderRepository.save(created)

        subscription.status = RecurringOrderStatus.ACTIVE
        subscription.nextOrderAt = Instant.now().plus(subscription.cadenceDays.toLong(), ChronoUnit.DAYS)
        subscription.lastRemindedAt = null
        subscription.updatedAt = Instant.now()
        val saved = repository.save(subscription)
        publish(
            "RecurringOrderConfirmed",
            saved,
            mapOf(
                "createdOrderId" to requireNotNull(created.orderId).toString(),
                "createdOrderStatus" to created.status.name,
                "automaticCharge" to false,
                "normalOrderLifecycle" to true,
            )
        )
        return RecurringOrderConfirmation(saved.toView(), validation, requireNotNull(created.orderId))
    }

    @Transactional
    fun markDueForConfirmation(now: Instant = Instant.now()): Int {
        val due = repository.findByStatusAndNextOrderAtLessThanEqual(RecurringOrderStatus.ACTIVE, now)
        due.forEach { subscription ->
            subscription.status = RecurringOrderStatus.AWAITING_CONFIRMATION
            subscription.lastRemindedAt = now
            subscription.updatedAt = now
            repository.save(subscription)
            publish(
                "RecurringOrderConfirmationRequired",
                subscription,
                mapOf("nextOrderAt" to subscription.nextOrderAt.toString(), "automaticCharge" to false)
            )
        }
        return due.size
    }

    private fun owned(customerId: UUID, subscriptionId: UUID): RecurringOrderSubscription {
        val subscription = repository.findById(subscriptionId)
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
        status = status,
        nextOrderAt = nextOrderAt,
        lastRemindedAt = lastRemindedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        val ALLOWED_CADENCES = setOf(7, 15, 25, 30, 35)
    }
}
