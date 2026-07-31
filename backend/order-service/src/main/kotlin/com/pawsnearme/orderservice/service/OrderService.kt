package com.pawsnearme.orderservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.repository.*
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderAccessDeniedException(message: String) : RuntimeException(message)

data class ReorderValidationItem(
    val offeringId: UUID,
    val offeringName: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val isAvailable: Boolean,
    val message: String? = null
)

data class ReorderValidationResponse(
    val originalOrderId: UUID,
    val providerId: UUID,
    val isProviderServiceable: Boolean,
    val items: List<ReorderValidationItem>,
    val canReorder: Boolean
)

data class OrderItemRequest(
    val offeringId: UUID,
    @field:Min(1)
    @field:Max(99)
    val quantity: Int
)


data class CheckoutQuoteRequest(
    val customerId: UUID? = null,
    val providerId: UUID,
    val deliveryAddressId: UUID,
    @field:NotEmpty
    @field:Size(max = 50)
    @field:Valid
    val items: List<OrderItemRequest>,
    @field:Size(max = 64)
    val couponCode: String? = null,
    val loyaltyRewardId: UUID? = null,
    @field:Pattern(regexp = "(?i)CARD|UPI|COD")
    val paymentMethod: String? = null,
    @field:Size(max = 120)
    val city: String? = null,
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double? = null,
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double? = null
)

data class CheckoutQuoteResponse(
    val quoteToken: String,
    val subtotal: BigDecimal,
    val itemDiscount: BigDecimal,
    val couponDiscount: BigDecimal,
    val loyaltyDiscount: BigDecimal,
    val deliveryFee: BigDecimal,
    val tax: BigDecimal,
    val roundOff: BigDecimal,
    val payableTotal: BigDecimal,
    val couponCode: String? = null,
    val paymentMethod: String? = null,
    val isCodAvailable: Boolean = true,
    val codRejectionReason: String? = null,
    val expiresAt: Instant
)

data class CreateOrderRequest(
    val customerId: UUID? = null,
    val providerId: UUID,
    val deliveryAddressId: UUID,
    @field:NotEmpty
    @field:Size(max = 50)
    @field:Valid
    val items: List<OrderItemRequest>,
    @field:Size(max = 64)
    val couponCode: String? = null,
    val loyaltyRewardId: UUID? = null,
    @field:Pattern(regexp = "(?i)CARD|UPI|COD")
    val paymentMethod: String? = null,
    @field:Size(max = 128)
    val quoteToken: String? = null,
    @field:Size(max = 120)
    val city: String? = null,
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double? = null,
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double? = null
)


data class OrderPlacedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String = "OrderPlaced",
    val orderId: UUID,
    val actorId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val merchantOwnerUserId: UUID? = null,
    val totalAmount: BigDecimal,
    val occurredAt: Instant = Instant.now()
)

data class OrderStatusChangedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val orderId: UUID,
    val actorId: UUID,
    val fromStatus: String,
    val toStatus: String,
    val totalAmount: BigDecimal,
    val deliveryFee: BigDecimal,
    val captainId: UUID? = null,
    val providerId: UUID? = null,
    val merchantOwnerUserId: UUID? = null,
    val occurredAt: Instant = Instant.now()
)

data class CustomerOrderSummary(
    val orderId: UUID,
    val providerId: UUID,
    val status: OrderStatus,
    val flowStep: String,
    val totalAmount: BigDecimal,
    val placedAt: Instant,
    val items: List<String>,
    val statusHistory: List<OrderStatusHistoryEntry>,
)

data class OrderStatusHistoryEntry(
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus,
    val changedAt: Instant,
    val note: String?,
)

data class SupportCaseEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val supportCaseId: UUID,
    val actorId: UUID?,
    val actionType: String,
    val status: String,
    val occurredAt: Instant = Instant.now()
)

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val systemConfigRepository: SystemConfigRepository,
    private val disputeRepository: DisputeRepository,
    private val invoiceRepository: InvoiceRepository,
    private val supportCaseRepository: SupportCaseRepository,
    private val outboxService: OutboxService,
    @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}")
    private val catalogServiceUrl: String,
    @Value("\${PAYMENT_SERVICE_URL:http://localhost:8090}")
    private val paymentServiceUrl: String,
    @Value("\${PROVIDER_SERVICE_URL:http://localhost:8081}")
    private val providerServiceUrl: String,
    @Value("\${DISCOVERY_SERVICE_URL:http://localhost:8083}")
    private val discoveryServiceUrl: String,
    @Value("\${gateway.trust.secret:}")
    private val gatewayTrustSecret: String = "",
    @Value("\${order.online-payments-enabled:false}")
    private val onlinePaymentsEnabled: Boolean = false,
    private val restTemplate: RestTemplate,
    private val quoteStore: QuoteStore? = null
) {
    fun calculateQuote(request: CheckoutQuoteRequest): CheckoutQuoteResponse {
        validateItems(request.items, "Quote")
        val paymentMethod = normalizePaymentMethod(request.paymentMethod)
        validateServiceability(request.city, request.latitude, request.longitude)

        var subtotal = BigDecimal.ZERO
        for (item in request.items) {
            val offering = fetchOfferingSnapshot(item.offeringId)
            if (offering.providerId != request.providerId) {
                throw IllegalArgumentException("All checkout items must belong to the selected provider")
            }
            if (offering.status != "ACTIVE") {
                throw IllegalArgumentException("Offering ${item.offeringId} is not available")
            }
            val availableStock = offering.stockQuantity
                ?: throw IllegalArgumentException("Offering ${item.offeringId} is not a delivery product")
            if (availableStock < item.quantity) {
                throw IllegalArgumentException("Insufficient stock for offering ${item.offeringId}")
            }
            val lineTotal = offering.price.multiply(BigDecimal(item.quantity))
            subtotal = subtotal.add(lineTotal)
        }

        var couponDiscount = BigDecimal.ZERO
        if (!request.couponCode.isNullOrBlank()) {
            couponDiscount = validateCouponDiscount(
                request.couponCode.trim().uppercase(),
                subtotal,
                request.providerId
            )
        }

        val itemDiscount = BigDecimal.ZERO
        val loyaltyDiscount = BigDecimal.ZERO

        val deliveryFee = if (subtotal >= BigDecimal("500.00")) BigDecimal.ZERO else BigDecimal("49.00")

        val taxableBase = subtotal.subtract(couponDiscount).subtract(itemDiscount).subtract(loyaltyDiscount).max(BigDecimal.ZERO)
        val tax = taxableBase.multiply(BigDecimal("0.05")).setScale(2, java.math.RoundingMode.HALF_UP)
        val roundOff = BigDecimal.ZERO

        val payableTotal = subtotal
            .subtract(couponDiscount)
            .subtract(itemDiscount)
            .subtract(loyaltyDiscount)
            .add(deliveryFee)
            .add(tax)
            .add(roundOff)
            .setScale(2, java.math.RoundingMode.HALF_UP)

        if (payableTotal < BigDecimal.ZERO) {
            throw IllegalArgumentException("Calculated order total cannot be negative")
        }

        var isCodAvailable = true
        var codRejectionReason: String? = null

        if (paymentMethod == "COD") {
            val codCheck = checkCodEligibility(payableTotal, request.city, request.providerId)
            isCodAvailable = codCheck.first
            codRejectionReason = codCheck.second
        }

        val quoteToken = "Q-${UUID.randomUUID().toString().take(12)}"
        val expiresAt = Instant.now().plusSeconds(900)

        // Store the computed total in Redis so createOrder() can validate it.
        // This prevents the client from re-submitting the same token multiple times
        // or placing orders after prices have changed.
        quoteStore?.store(quoteToken, payableTotal, request.couponCode?.trim()?.uppercase())

        return CheckoutQuoteResponse(
            quoteToken = quoteToken,
            subtotal = subtotal,
            itemDiscount = itemDiscount,
            couponDiscount = couponDiscount,
            loyaltyDiscount = loyaltyDiscount,
            deliveryFee = deliveryFee,
            tax = tax,
            roundOff = roundOff,
            payableTotal = payableTotal,
            couponCode = request.couponCode?.trim()?.uppercase(),
            paymentMethod = paymentMethod,
            isCodAvailable = isCodAvailable,
            codRejectionReason = codRejectionReason,
            expiresAt = expiresAt
        )
    }

    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        validateItems(request.items, "Order")
        val paymentMethod = normalizePaymentMethod(request.paymentMethod)
        if (paymentMethod != "COD" && !onlinePaymentsEnabled) {
            throw IllegalStateException(
                "Online checkout is temporarily unavailable. Select cash on delivery."
            )
        }

        // If a quoteToken was provided by the client, validate it against the Redis store.
        // This prevents price-manipulation: the token locks in the total computed at quote time.
        // If Redis is unavailable (quoteStore is null), fall through to live recalculation.
        val quote = if (!request.quoteToken.isNullOrBlank() && quoteStore != null) {
            val snapshot = quoteStore.consume(request.quoteToken)
                ?: throw IllegalArgumentException(
                    "Quote token '${request.quoteToken}' has expired or was already used. " +
                        "Request a new quote before placing your order."
                )
            // Re-validate COD eligibility with the stored total (city may still apply)
            calculateQuote(
                CheckoutQuoteRequest(
                    customerId = request.customerId,
                    providerId = request.providerId,
                    deliveryAddressId = request.deliveryAddressId,
                    items = request.items,
                    couponCode = request.couponCode,
                    loyaltyRewardId = request.loyaltyRewardId,
                    paymentMethod = paymentMethod,
                    city = request.city,
                    latitude = request.latitude,
                    longitude = request.longitude
                )
            ).also { freshQuote ->
                // Guard: reject if live price differs from the locked-in quote by more than ₹1
                if (freshQuote.payableTotal.subtract(snapshot.total).abs() > BigDecimal("1.00")) {
                    throw IllegalStateException(
                        "Price has changed since your quote. Please request a new quote."
                    )
                }
            }
        } else {
            calculateQuote(
                CheckoutQuoteRequest(
                    customerId = request.customerId,
                    providerId = request.providerId,
                    deliveryAddressId = request.deliveryAddressId,
                    items = request.items,
                    couponCode = request.couponCode,
                    loyaltyRewardId = request.loyaltyRewardId,
                    paymentMethod = paymentMethod,
                    city = request.city,
                    latitude = request.latitude,
                    longitude = request.longitude
                )
            )
        }

        val isCod = paymentMethod == "COD"
        if (isCod && !quote.isCodAvailable) {
            throw IllegalArgumentException("COD_NOT_ELIGIBLE: ${quote.codRejectionReason ?: "Order total exceeds COD limit"}")
        }

        val activeCustomerId = requireNotNull(request.customerId) { "Missing required customerId context" }
        val reservedItems = mutableListOf<OrderItemRequest>()
        var couponReserved = false
        var savedOrderId: UUID? = null

        try {
            val orderItemsToSave = mutableListOf<OrderItem>()

            for (item in request.items) {
                val cartEntry = decrementCatalogStock(item.offeringId, item.quantity, catalogServiceUrl)
                reservedItems.add(item)
                orderItemsToSave.add(cartEntry)
            }

            val initialStatus = if (isCod) OrderStatus.ACCEPTED else OrderStatus.PLACED
            val paymentStatus = if (isCod) "COD_PENDING" else "PENDING"

            val order = Order(
                customerId = activeCustomerId,
                providerId = request.providerId,
                deliveryAddressId = request.deliveryAddressId,
                status = initialStatus,
                subtotalAmount = quote.subtotal,
                deliveryFee = quote.deliveryFee,
                discountAmount = quote.couponDiscount.add(quote.itemDiscount).add(quote.loyaltyDiscount),
                taxAmount = quote.tax,
                totalAmount = quote.payableTotal,
                couponCode = quote.couponCode,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus
            )
            val savedOrder = orderRepository.save(order)
            savedOrderId = savedOrder.orderId

            for (item in orderItemsToSave) {
                item.orderId = savedOrder.orderId!!
                orderItemRepository.save(item)
            }

            if (quote.couponCode != null) {
                val reservedDiscount = reserveCouponDiscount(
                    quote.couponCode,
                    quote.subtotal,
                    request.providerId,
                    request.customerId,
                    savedOrder.orderId!!
                )
                couponReserved = true
                if (reservedDiscount.compareTo(quote.couponDiscount) != 0) {
                    throw IllegalStateException("Coupon pricing changed before order placement. Please request a new quote.")
                }
            }

            logStatusChange(
                savedOrder.orderId!!,
                null,
                initialStatus,
                savedOrder.customerId,
                "Order placed successfully"
            )

            val event = OrderPlacedEvent(
                orderId = savedOrder.orderId!!,
                actorId = savedOrder.customerId,
                customerId = savedOrder.customerId,
                providerId = savedOrder.providerId,
                merchantOwnerUserId = fetchProviderOwnerUserId(savedOrder.providerId),
                totalAmount = savedOrder.totalAmount
            )
            outboxService.saveEvent(
                eventId = event.eventId,
                aggregateType = "ORDER",
                aggregateId = savedOrder.orderId!!,
                eventType = "OrderPlaced",
                eventPayload = event
            )

            return savedOrder
        } catch (e: Exception) {
            if (couponReserved && !request.couponCode.isNullOrBlank() && savedOrderId != null) {
                releaseCouponReservation(
                    request.couponCode.trim().uppercase(),
                    activeCustomerId,
                    savedOrderId
                )
            }
            restoreReservedCatalogStock(reservedItems)
            throw e
        }
    }

    fun confirmOrder(orderId: UUID, paymentId: UUID?): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }

        if (order.status == OrderStatus.ACCEPTED) {
            return order
        }

        if (order.status != OrderStatus.PLACED) {
            throw IllegalStateException("Order is not in PLACED state. Current state: ${order.status}")
        }

        val paymentIdToUse = paymentId
            ?: throw IllegalArgumentException("Payment ID is required to confirm order")

        try {
            val url = "$paymentServiceUrl/api/v1/payments/transactions/$paymentIdToUse"
            val headers = internalHeaders()
            headers.set("X-User-Role", "ADMIN")
            val entity = org.springframework.http.HttpEntity<Any>(headers)
            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)
            val tx = response.body ?: throw IllegalStateException("Payment transaction $paymentIdToUse not found")

            val status = tx["status"] as? String

            if (status != "SUCCESS") {
                throw IllegalStateException("Payment status is $status, but expected SUCCESS to confirm order")
            }
            // Use BigDecimal.compareTo() to avoid floating-point rounding errors
            // when comparing monetary amounts (e.g. 999.95 as Double != 999.95 exactly).
            val paymentAmount = when (val raw = tx["amount"]) {
                is BigDecimal -> raw
                is Number -> BigDecimal(raw.toString())
                else -> throw IllegalStateException("Cannot parse payment amount from transaction response")
            }
            if (paymentAmount.compareTo(order.totalAmount) != 0) {
                throw IllegalStateException(
                    "Payment amount \u20b9$paymentAmount does not match order total \u20b9${order.totalAmount}"
                )
            }
        } catch (e: Exception) {
            throw IllegalStateException("Payment verification failed: ${e.message}", e)
        }

        if (order.couponCode != null) {
            redeemCouponReservation(order.couponCode!!, order.customerId, order.orderId!!)
        }

        val oldStatus = order.status
        order.status = OrderStatus.ACCEPTED
        order.paymentId = paymentIdToUse
        order.paymentStatus = "SUCCESS"
        order.acceptedAt = Instant.now()
        val saved = orderRepository.save(order)

        logStatusChange(saved.orderId!!, oldStatus, OrderStatus.ACCEPTED, saved.customerId, "Order confirmed and paid")

        val event = OrderStatusChangedEvent(
            eventType = "OrderStatusChanged",
            orderId = saved.orderId!!,
            actorId = saved.customerId,
            fromStatus = oldStatus.name,
            toStatus = OrderStatus.ACCEPTED.name,
            totalAmount = saved.totalAmount,
            deliveryFee = saved.deliveryFee,
            captainId = saved.captainId,
            providerId = saved.providerId,
            merchantOwnerUserId = fetchProviderOwnerUserId(saved.providerId),
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "ORDER",
            aggregateId = saved.orderId!!,
            eventType = "OrderStatusChanged",
            eventPayload = event
        )

        return saved
    }

    fun updateOrderStatus(orderId: UUID, newStatus: OrderStatus, changedBy: UUID, note: String? = null): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        
        val oldStatus = order.status
        if (oldStatus == newStatus) {
            return order
        }
        if (oldStatus in setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED)) {
            throw IllegalStateException("Order in terminal state $oldStatus cannot transition to $newStatus")
        }
        if (shouldRestoreReservedStock(oldStatus, newStatus)) {
            restoreOrderCatalogStock(orderId)
        }
        order.status = newStatus
        
        when (newStatus) {
            OrderStatus.ACCEPTED -> order.acceptedAt = Instant.now()
            OrderStatus.ASSIGNED, OrderStatus.REASSIGNED -> order.captainId = changedBy
            OrderStatus.READY_FOR_PICKUP -> order.readyAt = Instant.now()
            OrderStatus.PICKED_UP -> order.picked_upAt = Instant.now()
            OrderStatus.DELIVERED -> {
                if (order.paymentMethod == "COD") {
                    order.couponCode?.let {
                        redeemCouponReservation(it, order.customerId, order.orderId!!)
                    }
                    order.paymentStatus = "COD_COLLECTED"
                }
                order.deliveredAt = Instant.now()
                generateInvoiceForOrder(order)
                notifyLoyaltyOrderDelivered(order)
            }
            OrderStatus.COMPLETED -> { /* terminal state */ }
            OrderStatus.CANCELLED -> {
                order.cancelledAt = Instant.now()
                order.cancellationReason = note
            }
            else -> {}
        }
        
        val updatedOrder = orderRepository.save(order)
        logStatusChange(orderId, oldStatus, newStatus, changedBy, note)

        val event = OrderStatusChangedEvent(
            eventType = when (newStatus) {
                OrderStatus.CANCELLED -> "OrderCancelled"
                else -> "OrderStatusChanged"
            },
            orderId = orderId,
            actorId = changedBy,
            fromStatus = oldStatus.name,
            toStatus = newStatus.name,
            totalAmount = updatedOrder.totalAmount,
            deliveryFee = updatedOrder.deliveryFee,
            captainId = updatedOrder.captainId,
            providerId = updatedOrder.providerId,
            merchantOwnerUserId = fetchProviderOwnerUserId(updatedOrder.providerId),
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "ORDER",
            aggregateId = orderId,
            eventType = event.eventType,
            eventPayload = event
        )

        return updatedOrder
    }

    private fun logStatusChange(orderId: UUID, fromStatus: OrderStatus?, toStatus: OrderStatus, changedByUserId: UUID, note: String?) {
        val history = OrderStatusHistory(
            orderId = orderId,
            fromStatus = fromStatus,
            toStatus = toStatus,
            changedByUserId = changedByUserId,
            note = note
        )
        orderStatusHistoryRepository.save(history)
    }

    @CircuitBreaker(name = "catalogService", fallbackMethod = "decrementCatalogStockFallback")
    @Retry(name = "catalogService")
    private fun decrementCatalogStock(offeringId: UUID, quantity: Int, baseUrl: String): OrderItem {
        if (quantity <= 0) {
            throw IllegalArgumentException("Quantity must be greater than zero")
        }
        val url = "$baseUrl/api/v1/catalog/offerings/$offeringId/decrement-stock?quantity=$quantity"
        val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())
        val response = restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.PUT,
            entity,
            Map::class.java
        ).body ?: throw IllegalStateException("Catalog service returned empty response")

        val price = parseCatalogPrice(response["price"])
        val name = response["name"] as? String ?: "Pet Product"

        return OrderItem(
            orderId = UUID.randomUUID(),
            offeringId = offeringId,
            offeringNameSnapshot = name,
            unitPriceSnapshot = price,
            quantity = quantity,
            lineTotal = price.multiply(BigDecimal(quantity))
        )
    }

    fun decrementCatalogStockFallback(offeringId: UUID, quantity: Int, baseUrl: String, e: Throwable): OrderItem {
        logger.error("Catalog Service decrement call failed (Circuit Breaker fallback active): ${e.message}")
        throw IllegalStateException("Catalog service is currently unavailable (circuit open). Please try again later.", e)
    }

    private fun restoreReservedCatalogStock(items: List<OrderItemRequest>) {
        for (item in items.asReversed()) {
            try {
                val url = "$catalogServiceUrl/api/v1/catalog/offerings/${item.offeringId}/restore-stock?quantity=${item.quantity}"
                val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())
                restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.PUT,
                    entity,
                    Map::class.java
                )
            } catch (e: Exception) {
                logger.error("WARNING: Failed to restore catalog stock for offering ${item.offeringId}: ${e.message}")
            }
        }
    }

    private fun restoreOrderCatalogStock(orderId: UUID) {
        val orderItems = orderItemRepository.findByOrderId(orderId)
        restoreReservedCatalogStock(orderItems.map { OrderItemRequest(it.offeringId, it.quantity) })
    }

    private fun shouldRestoreReservedStock(oldStatus: OrderStatus, newStatus: OrderStatus): Boolean {
        val releasingStatuses = setOf(OrderStatus.CANCELLED, OrderStatus.REJECTED)
        return newStatus in releasingStatuses && oldStatus !in releasingStatuses
    }

    private fun parseCatalogPrice(value: Any?): BigDecimal {
        return when (value) {
            is BigDecimal -> value
            is Number -> BigDecimal.valueOf(value.toDouble())
            is String -> value.toBigDecimal()
            else -> throw IllegalStateException("Catalog service returned invalid offering price")
        }
    }

    fun getDisputeRefundMode(): String {
        return systemConfigRepository.findById("dispute_refund_mode")
            .map { it.configValue }
            .orElse("MANUAL")
    }

    fun updateDisputeRefundMode(value: String): String {
        if (value != "MANUAL" && value != "AUTOMATED") {
            throw IllegalArgumentException("Invalid mode. Allowed: MANUAL, AUTOMATED")
        }
        val config = systemConfigRepository.findById("dispute_refund_mode")
            .orElseGet { SystemConfig("dispute_refund_mode", "MANUAL") }
        config.configValue = value
        config.updatedAt = Instant.now()
        systemConfigRepository.save(config)
        return value
    }

    fun createDispute(orderId: UUID, reason: String): Dispute {
        if (!orderRepository.existsById(orderId)) {
            throw IllegalArgumentException("Order with ID $orderId not found")
        }
        val dispute = Dispute(
            orderId = orderId,
            status = "OPEN",
            reason = reason
        )
        return disputeRepository.save(dispute)
    }

    fun listDisputes(): List<Dispute> {
        return disputeRepository.findAll()
    }

    fun getInvoiceByOrderId(orderId: UUID): Invoice {
        return invoiceRepository.findByOrderId(orderId)
            .orElseThrow { NoSuchElementException("Invoice not found for order $orderId") }
    }

    fun resolveDispute(disputeId: UUID, decision: String, resolutionNotes: String?): Dispute {
        val dispute = disputeRepository.findById(disputeId)
            .orElseThrow { NoSuchElementException("Dispute not found for ID $disputeId") }

        if (dispute.status != "OPEN") {
            throw IllegalStateException("Dispute is already resolved")
        }

        dispute.status = decision
        dispute.resolutionNotes = resolutionNotes
        dispute.resolvedAt = Instant.now()
        val savedDispute = disputeRepository.save(dispute)

        if (decision == "RESOLVED") {
            val mode = getDisputeRefundMode()
            if (mode == "AUTOMATED") {
                triggerPaymentRefund(dispute.orderId)
            }
        }

        return savedDispute
    }

    fun listSupportCases(): List<SupportCase> {
        return supportCaseRepository.findAllByOrderByCreatedAtDesc()
    }

    fun createSupportCase(
        title: String,
        detail: String,
        actionType: String,
        entityType: String?,
        entityId: UUID?,
        createdByUserId: UUID?
    ): SupportCase {
        if (title.isBlank()) {
            throw IllegalArgumentException("Support case title is required")
        }
        if (detail.isBlank()) {
            throw IllegalArgumentException("Support case detail is required")
        }

        val normalizedActionType = normalizeSupportActionType(actionType)
        val supportCase = SupportCase(
            title = title.trim(),
            detail = detail.trim(),
            actionType = normalizedActionType,
            entityType = entityType?.trim()?.uppercase()?.ifBlank { null },
            entityId = entityId,
            status = "OPEN",
            createdByUserId = createdByUserId
        )
        val saved = supportCaseRepository.save(supportCase)
        publishSupportCaseEvent("SupportCaseOpened", saved)
        return saved
    }

    fun resolveSupportCase(supportCaseId: UUID, resolutionNotes: String?, actorId: UUID?): SupportCase {
        val supportCase = supportCaseRepository.findById(supportCaseId)
            .orElseThrow { NoSuchElementException("Support case not found for ID $supportCaseId") }

        if (supportCase.status != "OPEN") {
            throw IllegalStateException("Support case is already resolved")
        }

        supportCase.status = "RESOLVED"
        supportCase.resolutionNotes = resolutionNotes
        supportCase.resolvedAt = Instant.now()
        val saved = supportCaseRepository.save(supportCase)
        publishSupportCaseEvent("SupportCaseResolved", saved, actorId)
        return saved
    }

    private fun normalizeSupportActionType(actionType: String): String {
        val normalized = actionType.trim().uppercase()
        val allowed = setOf(
            "INFO_REQUEST",
            "REFUND_ESCALATION",
            "PAYOUT_CLAIM_REVIEW",
            "CUSTOMER_CALLBACK",
            "GENERAL"
        )
        if (normalized !in allowed) {
            throw IllegalArgumentException("Invalid support action type. Allowed: ${allowed.joinToString(", ")}")
        }
        return normalized
    }

    private fun publishSupportCaseEvent(eventType: String, supportCase: SupportCase, actorId: UUID? = supportCase.createdByUserId) {
        val event = SupportCaseEvent(
            eventType = eventType,
            supportCaseId = supportCase.supportCaseId!!,
            actorId = actorId,
            actionType = supportCase.actionType,
            status = supportCase.status
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "SUPPORT",
            aggregateId = supportCase.supportCaseId!!,
            eventType = eventType,
            eventPayload = event
        )
    }

    private fun triggerPaymentRefund(orderId: UUID) {
        try {
            val url = "$paymentServiceUrl/api/v1/payments/refund?orderId=$orderId"
            val headers = internalHeaders()
            headers.set("X-User-Role", "ADMIN")
            val entity = org.springframework.http.HttpEntity<Any>(headers)
            restTemplate.postForEntity(url, entity, String::class.java)
            logger.info("Dispute System: Triggered automated refund for order $orderId")
        } catch (e: Exception) {
            logger.error("WARNING: Failed to call payment-service refund endpoint: ${e.message}")
        }
    }

    private fun generateInvoiceForOrder(order: Order) {
        if (!invoiceRepository.findByOrderId(order.orderId!!).isPresent) {
            val subtotal = order.subtotalAmount
            val tax = order.taxAmount
            val total = order.totalAmount

            val year = java.time.LocalDate.now().year
            val suffix = order.orderId.toString().substring(0, 8).uppercase()
            val invoiceNumber = "INV-$year-$suffix"

            val invoice = Invoice(
                orderId = order.orderId!!,
                invoiceNumber = invoiceNumber,
                subtotalAmount = subtotal,
                taxAmount = tax,
                totalAmount = total
            )
            invoiceRepository.save(invoice)
            logger.info("Invoicing: Generated invoice $invoiceNumber for order ${order.orderId}")
        }
    }

    fun getOrderWithAuth(id: UUID, callerId: UUID, callerRole: String?): Order {
        val order = orderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Order with ID $id not found") }
        assertCanAccessOrder(order, callerId, callerRole)
        return order
    }

    fun getOrdersByCustomerWithAuth(customerId: UUID, callerId: UUID, callerRole: String?): List<Order> {
        assertCanAccessCustomerOrders(customerId, callerId, callerRole)
        return orderRepository.findByCustomerId(customerId)
    }

    fun getOrdersByProviderWithAuth(providerId: UUID, callerId: UUID, callerRole: String?): List<Order> {
        val normalizedRole = callerRole?.uppercase()
        val isAdmin = normalizedRole == "ADMIN"
        val isProviderOwner = normalizedRole == "MERCHANT" && fetchProviderOwnerUserId(providerId) == callerId
        if (!isAdmin && !isProviderOwner) {
            throw OrderAccessDeniedException("Access denied to provider orders.")
        }
        return orderRepository.findByProviderId(providerId)
    }

    fun getCustomerOrderSummariesWithAuth(customerId: UUID, callerId: UUID, callerRole: String?): List<CustomerOrderSummary> {
        assertCanAccessCustomerOrders(customerId, callerId, callerRole)
        return getCustomerOrderSummaries(customerId)
    }

    fun updateOrderStatusWithAuth(
        orderId: UUID,
        newStatus: OrderStatus,
        callerId: UUID,
        callerRole: String?,
        note: String?
    ): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        val normalizedRole = callerRole?.uppercase()
        val allowed = when (normalizedRole) {
            "ADMIN" -> true
            "MERCHANT" -> {
                fetchProviderOwnerUserId(order.providerId) == callerId &&
                    newStatus in setOf(
                        OrderStatus.ACCEPTED,
                        OrderStatus.PREPARING,
                        OrderStatus.READY_FOR_PICKUP,
                        OrderStatus.CANCELLED,
                        OrderStatus.REJECTED
                    )
            }
            "CAPTAIN" -> {
                order.captainId == callerId &&
                    newStatus in setOf(OrderStatus.PICKED_UP, OrderStatus.DELIVERED)
            }
            else -> false
        }
        if (!allowed) {
            throw OrderAccessDeniedException("Access denied for this order status transition.")
        }
        return updateOrderStatus(orderId, newStatus, callerId, note)
    }

    fun confirmOrderWithAuth(
        orderId: UUID,
        paymentId: UUID?,
        callerId: UUID,
        callerRole: String?
    ): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        assertCanAccessOrder(order, callerId, callerRole)
        return confirmOrder(orderId, paymentId)
    }

    fun cancelOrder(orderId: UUID, callerId: UUID, callerRole: String?, reason: String?): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }

        assertCanAccessOrder(order, callerId, callerRole)

        val cancellable = setOf(OrderStatus.PLACED, OrderStatus.ACCEPTED)
        if (order.status !in cancellable) {
            throw IllegalStateException("Order in status ${order.status} cannot be cancelled.")
        }

        if (order.couponCode != null) {
            releaseCouponReservation(order.couponCode!!, order.customerId, order.orderId!!)
        }

        return updateOrderStatus(orderId, OrderStatus.CANCELLED, callerId, reason ?: "Cancelled by user")
    }

    fun revalidateReorder(orderId: UUID, callerId: UUID, callerRole: String?): ReorderValidationResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }

        assertCanAccessOrder(order, callerId, callerRole)

        val items = orderItemRepository.findByOrderId(orderId)
        val validatedItems = mutableListOf<ReorderValidationItem>()
        var allAvailable = true

        for (item in items) {
            var available = true
            var msg: String? = null
            var currentPrice = item.unitPriceSnapshot
            var name = item.offeringNameSnapshot

            try {
                val url = "$catalogServiceUrl/api/v1/catalog/offerings/${item.offeringId}"
                val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())
                val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
                if (response != null) {
                    val status = response["status"] as? String ?: "ACTIVE"
                    val stock = (response["stockQuantity"] as? Number)?.toInt() ?: 0
                    currentPrice = parseCatalogPrice(response["price"])
                    name = response["name"] as? String ?: name

                    if (status != "ACTIVE") {
                        available = false
                        msg = "Offering is no longer active"
                    } else if (stock < item.quantity) {
                        available = false
                        msg = "Insufficient stock ($stock available)"
                    }
                } else {
                    available = false
                    msg = "Offering not found in catalog"
                }
            } catch (e: Exception) {
                logger.warn("Reorder offering check failed for {}: {}", item.offeringId, e.message)
            }

            if (!available) allAvailable = false
            validatedItems.add(
                ReorderValidationItem(
                    offeringId = item.offeringId,
                    offeringName = name,
                    unitPrice = currentPrice,
                    quantity = item.quantity,
                    isAvailable = available,
                    message = msg
                )
            )
        }

        val isServiceable = true // Provider active
        return ReorderValidationResponse(
            originalOrderId = orderId,
            providerId = order.providerId,
            isProviderServiceable = isServiceable,
            items = validatedItems,
            canReorder = allAvailable && isServiceable
        )
    }

    private fun assertCanAccessOrder(order: Order, callerId: UUID, callerRole: String?) {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isCustomer = order.customerId == callerId
        val isProviderOwner = if (callerRole?.uppercase() == "MERCHANT") {
            val ownerId = fetchProviderOwnerUserId(order.providerId)
            ownerId == callerId
        } else {
            false
        }
        if (!isAdmin && !isCustomer && !isProviderOwner) {
            throw OrderAccessDeniedException("Access denied to order data.")
        }
    }

    private fun assertCanAccessCustomerOrders(targetCustomerId: UUID, callerId: UUID, callerRole: String?) {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isCustomer = targetCustomerId == callerId
        if (!isAdmin && !isCustomer) {
            throw OrderAccessDeniedException("Access denied to customer order history.")
        }
    }

    fun getCustomerOrderSummaries(customerId: UUID): List<CustomerOrderSummary> {

        return orderRepository.findByCustomerId(customerId).map { order ->
            val items = orderItemRepository.findByOrderId(order.orderId!!)
            val history = orderStatusHistoryRepository.findByOrderId(order.orderId!!)
            CustomerOrderSummary(
                orderId = order.orderId!!,
                providerId = order.providerId,
                status = order.status,
                flowStep = mapFlowStep(order.status),
                totalAmount = order.totalAmount,
                placedAt = order.placedAt,
                items = items.map { it.offeringNameSnapshot },
                statusHistory = history.map {
                    OrderStatusHistoryEntry(it.fromStatus, it.toStatus, it.changedAt, it.note)
                },
            )
        }.sortedByDescending { it.placedAt }
    }

    private fun fetchProviderOwnerUserId(providerId: UUID): UUID? {
        return try {
            val url = "$providerServiceUrl/api/v1/providers/$providerId"
            val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())
            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)
            val owner = response.body?.get("ownerUserId") as? String ?: return null
            UUID.fromString(owner)
        } catch (e: Exception) {
            logger.warn("Could not resolve provider owner for {}: {}", providerId, e.message)
            null
        }
    }

    private fun mapFlowStep(status: OrderStatus): String = when (status) {
        OrderStatus.PLACED, OrderStatus.ACCEPTED -> "placed"
        OrderStatus.ASSIGNED, OrderStatus.REASSIGNED -> "assigned"
        OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP -> "packed"
        OrderStatus.PICKED_UP -> "picked"
        OrderStatus.DELIVERED -> "delivered"
        OrderStatus.COMPLETED -> "completed"
        else -> "placed"
    }

    private data class CatalogOfferingSnapshot(
        val providerId: UUID,
        val price: BigDecimal,
        val status: String,
        val stockQuantity: Int?
    )

    private fun fetchOfferingSnapshot(offeringId: UUID): CatalogOfferingSnapshot {
        val url = "$catalogServiceUrl/api/v1/catalog/offerings/$offeringId"
        val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())
        val response = try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
        } catch (e: Exception) {
            logger.warn("Catalog lookup failed for {}: {}", offeringId, e.message)
            throw IllegalStateException("Catalog service is unavailable. Please try checkout again.", e)
        } ?: throw IllegalStateException("Catalog service returned an empty offering response")

        val responseProviderId = response["providerId"]?.toString()?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: throw IllegalStateException("Catalog service returned an invalid provider")

        return CatalogOfferingSnapshot(
            providerId = responseProviderId,
            price = parseCatalogPrice(response["price"]),
            status = response["status"]?.toString()?.uppercase()
                ?: throw IllegalStateException("Catalog service returned an invalid offering status"),
            stockQuantity = (response["stockQuantity"] as? Number)?.toInt()
        )
    }

    private fun validateCouponDiscount(code: String, subtotal: BigDecimal, providerId: UUID): BigDecimal {
        val url = UriComponentsBuilder
            .fromUriString("$paymentServiceUrl/api/v1/payments/promotions/validate")
            .queryParam("code", code)
            .queryParam("orderValue", subtotal)
            .queryParam("providerId", providerId)
            .build()
            .encode()
            .toUriString()
        val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())
        val promo = try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
        } catch (e: Exception) {
            logger.info("Coupon validation rejected code {}: {}", code, e.message)
            throw IllegalArgumentException("Coupon is invalid, expired, or not applicable to this order")
        } ?: throw IllegalArgumentException("Coupon validation returned no result")

        val discountType = promo["discountType"]?.toString()?.uppercase()
        val discountValue = parseCatalogPrice(promo["discountValue"])
        return when (discountType) {
            "PERCENTAGE" -> {
                val raw = subtotal
                    .multiply(discountValue)
                    .divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                val maximum = promo["maxDiscountAmount"]?.let(::parseCatalogPrice)
                maximum?.let(raw::min) ?: raw
            }
            "FLAT" -> discountValue.min(subtotal)
            else -> throw IllegalStateException("Payment service returned an invalid coupon type")
        }
    }

    private fun reserveCouponDiscount(
        code: String,
        subtotal: BigDecimal,
        providerId: UUID,
        userId: UUID,
        orderId: UUID
    ): BigDecimal {
        val url = "$paymentServiceUrl/api/v1/payments/promotions/reserve"
        val headers = internalHeaders()
        headers.set("X-User-Role", "CUSTOMER")
        headers.set("X-User-Id", userId.toString())
        val body = mapOf(
            "code" to code,
            "orderValue" to subtotal,
            "providerId" to providerId,
            "userId" to userId,
            "orderId" to orderId
        )
        val entity = org.springframework.http.HttpEntity(body, headers)
        val response = try {
            restTemplate.postForEntity(url, entity, Map::class.java).body
        } catch (e: Exception) {
            logger.info("Coupon reservation rejected code {} for order {}: {}", code, orderId, e.message)
            throw IllegalArgumentException("Coupon could not be reserved. Please request a new quote.")
        } ?: throw IllegalStateException("Payment service returned an empty coupon reservation")

        return parseCatalogPrice(response["discountAmount"])
    }

    private fun releaseCouponReservation(code: String, userId: UUID, orderId: UUID) {
        try {
            val url = UriComponentsBuilder
                .fromUriString("$paymentServiceUrl/api/v1/payments/promotions/release")
                .queryParam("code", code)
                .queryParam("userId", userId)
                .queryParam("orderId", orderId)
                .build()
                .encode()
                .toUriString()
            val headers = internalHeaders()
            headers.set("X-User-Role", "CUSTOMER")
            headers.set("X-User-Id", userId.toString())
            restTemplate.postForEntity(url, org.springframework.http.HttpEntity<Any>(headers), Map::class.java)
        } catch (e: Exception) {
            logger.error("Failed to release coupon reservation for order {}: {}", orderId, e.message)
        }
    }

    private fun redeemCouponReservation(code: String, userId: UUID, orderId: UUID) {
        val url = UriComponentsBuilder
            .fromUriString("$paymentServiceUrl/api/v1/payments/promotions/redeem")
            .queryParam("code", code)
            .queryParam("userId", userId)
            .queryParam("orderId", orderId)
            .build()
            .encode()
            .toUriString()
        val headers = internalHeaders()
        headers.set("X-User-Role", "ADMIN")
        restTemplate.postForEntity(
            url,
            org.springframework.http.HttpEntity<Any>(headers),
            Map::class.java
        )
    }

    private fun checkCodEligibility(amount: BigDecimal, city: String?, providerId: UUID?): Pair<Boolean, String?> {
        return try {
            val url = "$paymentServiceUrl/api/v1/payments/cod/check"
            val headers = internalHeaders()
            val body = mapOf(
                "amount" to amount,
                "city" to city,
                "providerId" to providerId
            )
            val entity = org.springframework.http.HttpEntity(body, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java).body
            val isEligible = response?.get("isEligible") as? Boolean
                ?: throw IllegalStateException("Payment service returned an invalid COD response")
            val reason = response?.get("reason") as? String
            Pair(isEligible, reason)
        } catch (e: Exception) {
            logger.warn("COD eligibility check failed: {}", e.message)
            Pair(false, "Cash on delivery is temporarily unavailable")
        }
    }

    private fun validateItems(items: List<OrderItemRequest>, subject: String) {
        if (items.isEmpty()) {
            throw IllegalArgumentException("$subject must contain at least one item")
        }
        if (items.size > 50) {
            throw IllegalArgumentException("$subject cannot contain more than 50 line items")
        }
        if (items.any { it.quantity !in 1..99 }) {
            throw IllegalArgumentException("Item quantities must be between 1 and 99")
        }
        if (items.map { it.offeringId }.distinct().size != items.size) {
            throw IllegalArgumentException("Duplicate offering IDs are not allowed")
        }
    }

    private fun normalizePaymentMethod(paymentMethod: String?): String {
        val normalized = paymentMethod?.trim()?.uppercase() ?: "CARD"
        if (normalized !in setOf("CARD", "UPI", "COD")) {
            throw IllegalArgumentException("Unsupported payment method")
        }
        return normalized
    }

    private fun validateServiceability(city: String?, latitude: Double?, longitude: Double?) {
        if (latitude == null && longitude != null || latitude != null && longitude == null) {
            throw IllegalArgumentException("Latitude and longitude must be provided together")
        }
        if (city.isNullOrBlank() && latitude == null) {
            return
        }

        val checkUrl = UriComponentsBuilder
            .fromUriString("$discoveryServiceUrl/api/v1/service-regions/check")
            .apply {
                if (!city.isNullOrBlank()) queryParam("city", city.trim())
                if (latitude != null) queryParam("latitude", latitude)
                if (longitude != null) queryParam("longitude", longitude)
            }
            .build()
            .encode()
            .toUriString()
        val response = try {
            restTemplate.getForObject(checkUrl, Map::class.java)
        } catch (e: Exception) {
            logger.warn("Serviceability lookup failed: {}", e.message)
            throw IllegalStateException("Delivery serviceability could not be verified. Please try again.", e)
        }
        val serviceable = response?.get("serviceable") as? Boolean
            ?: throw IllegalStateException("Discovery service returned an invalid serviceability response")
        if (!serviceable) {
            throw IllegalArgumentException("UNSERVICEABLE_REGION: Location is outside active service regions")
        }
    }

    private fun notifyLoyaltyOrderDelivered(order: Order) {
        try {
            val url = "$paymentServiceUrl/api/v1/loyalty/events/order-delivered"
            val headers = internalHeaders()
            val body = mapOf(
                "orderId" to order.orderId,
                "customerId" to order.customerId,
                "providerId" to order.providerId,
                "netAmount" to order.totalAmount
            )
            val entity = org.springframework.http.HttpEntity(body, headers)
            restTemplate.postForEntity(url, entity, Map::class.java)
        } catch (e: Exception) {
            logger.warn("Could not notify loyalty service for delivered order {}: {}", order.orderId, e.message)
        }
    }

    private fun notifyLoyaltyOrderRefunded(order: Order) {
        try {
            val url = "$paymentServiceUrl/api/v1/loyalty/events/order-refunded"
            val headers = internalHeaders()
            val body = mapOf(
                "orderId" to order.orderId,
                "customerId" to order.customerId,
                "providerId" to order.providerId
            )
            val entity = org.springframework.http.HttpEntity(body, headers)
            restTemplate.postForEntity(url, entity, Map::class.java)
        } catch (e: Exception) {
            logger.warn("Could not notify loyalty service for refunded order {}: {}", order.orderId, e.message)
        }
    }

    private fun internalHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        if (gatewayTrustSecret.isNotBlank()) {
            headers.set("X-Internal-Gateway-Secret", gatewayTrustSecret)
            headers.set("X-Internal-Secret", gatewayTrustSecret)
        }
        return headers
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OrderService::class.java)
    }
}
