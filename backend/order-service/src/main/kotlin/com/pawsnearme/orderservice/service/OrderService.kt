package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CouponReservationCommand
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.module.RemoteCatalogModuleApi
import com.pawsnearme.orderservice.module.RemoteDiscoveryModuleApi
import com.pawsnearme.orderservice.module.RemotePaymentModuleApi
import com.pawsnearme.orderservice.module.RemoteProviderModuleApi
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
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
class OrderService @Autowired constructor(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val systemConfigRepository: SystemConfigRepository,
    private val disputeRepository: DisputeRepository,
    private val invoiceRepository: InvoiceRepository,
    private val supportCaseRepository: SupportCaseRepository,
    private val outboxService: OutboxService,
    private val catalogModule: CatalogModuleApi,
    private val paymentModule: PaymentModuleApi,
    private val providerModule: ProviderModuleApi,
    private val discoveryModule: DiscoveryModuleApi,
    private val onlinePaymentsEnabled: Boolean = false,
    private val quoteStore: QuoteStore? = null,
    private val compensationService: OrderCompensationService? = null
) {
    /** Compatibility constructor retained for focused tests and distributed rollback tooling. */
    constructor(
        orderRepository: OrderRepository,
        orderItemRepository: OrderItemRepository,
        orderStatusHistoryRepository: OrderStatusHistoryRepository,
        kafkaTemplate: KafkaTemplate<String, Any>,
        systemConfigRepository: SystemConfigRepository,
        disputeRepository: DisputeRepository,
        invoiceRepository: InvoiceRepository,
        supportCaseRepository: SupportCaseRepository,
        outboxService: OutboxService,
        catalogServiceUrl: String,
        paymentServiceUrl: String,
        providerServiceUrl: String,
        discoveryServiceUrl: String,
        internalServiceSecret: String = "",
        onlinePaymentsEnabled: Boolean = false,
        restTemplate: RestTemplate,
        quoteStore: QuoteStore? = null,
        compensationService: OrderCompensationService? = null
    ) : this(
        orderRepository,
        orderItemRepository,
        orderStatusHistoryRepository,
        kafkaTemplate,
        systemConfigRepository,
        disputeRepository,
        invoiceRepository,
        supportCaseRepository,
        outboxService,
        RemoteCatalogModuleApi(restTemplate, catalogServiceUrl, internalServiceSecret),
        RemotePaymentModuleApi(restTemplate, paymentServiceUrl, internalServiceSecret),
        RemoteProviderModuleApi(restTemplate, providerServiceUrl, internalServiceSecret),
        RemoteDiscoveryModuleApi(restTemplate, discoveryServiceUrl),
        onlinePaymentsEnabled,
        quoteStore,
        compensationService
    )

    fun calculateQuote(request: CheckoutQuoteRequest): CheckoutQuoteResponse {
        validateItems(request.items, "Quote")
        val paymentMethod = normalizePaymentMethod(request.paymentMethod)
        validateServiceability(request.city, request.latitude, request.longitude)

        var subtotal = BigDecimal.ZERO
        var totalGst = BigDecimal.ZERO
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
            val lineSubtotal = offering.price.multiply(BigDecimal(item.quantity))
            subtotal = subtotal.add(lineSubtotal)
            val lineGst = lineSubtotal.multiply(offering.gstRate).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
            totalGst = totalGst.add(lineGst)
        }

        val couponDiscount = if (!request.couponCode.isNullOrBlank()) {
            validateCouponDiscount(request.couponCode.trim().uppercase(), subtotal, request.providerId)
        } else {
            BigDecimal.ZERO
        }
        val itemDiscount = BigDecimal.ZERO
        val loyaltyDiscount = BigDecimal.ZERO
        val deliveryFee = if (subtotal >= BigDecimal("500.00")) BigDecimal.ZERO else BigDecimal("49.00")
        val tax = totalGst
        val roundOff = BigDecimal.ZERO
        val payableTotal = subtotal
            .subtract(couponDiscount)
            .add(deliveryFee)
            .add(tax)
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
        quoteStore?.store(
            quoteToken,
            QuoteSnapshot(
                total = payableTotal,
                couponCode = request.couponCode?.trim()?.uppercase(),
                customerId = request.customerId ?: UUID(0, 0),
                providerId = request.providerId,
                paymentMethod = paymentMethod,
                deliveryAddressId = request.deliveryAddressId,
                loyaltyRewardId = request.loyaltyRewardId,
                items = request.items.map { QuoteItemSnapshot(it.offeringId, it.quantity) }
            )
        )

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
            throw IllegalStateException("Online checkout is temporarily unavailable. Select cash on delivery.")
        }
        if (request.quoteToken.isNullOrBlank()) {
            throw IllegalArgumentException("Quote token is mandatory for order creation")
        }

        val activeCustomerId = requireNotNull(request.customerId) { "Missing required customerId context" }
        val snapshot = quoteStore?.consume(request.quoteToken)
            ?: throw IllegalArgumentException(
                "Quote token '${request.quoteToken}' has expired, is invalid, or was already used. " +
                    "Request a new quote before placing your order."
            )

        if (snapshot.customerId != activeCustomerId) throw IllegalArgumentException("Quote token belongs to a different customer")
        if (snapshot.providerId != request.providerId) throw IllegalArgumentException("Quote token does not match order provider")
        if (snapshot.deliveryAddressId != request.deliveryAddressId) throw IllegalArgumentException("Delivery address does not match the quote")
        if (snapshot.loyaltyRewardId != request.loyaltyRewardId) throw IllegalArgumentException("Loyalty reward does not match the quote")
        val normalizedCoupon = request.couponCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        if (snapshot.couponCode != normalizedCoupon) throw IllegalArgumentException("Coupon does not match the quote")
        if (snapshot.paymentMethod != paymentMethod) throw IllegalArgumentException("Payment method does not match the quote")
        val reqItemsMap = request.items.associate { it.offeringId to it.quantity }
        val snapItemsMap = snapshot.items.associate { it.offeringId to it.quantity }
        if (reqItemsMap != snapItemsMap || reqItemsMap.size != request.items.size) {
            throw IllegalArgumentException("Order items do not match the locked quote")
        }

        val quote = calculateQuote(
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
            quoteStore?.delete(freshQuote.quoteToken)
            if (freshQuote.payableTotal.compareTo(snapshot.total) != 0) {
                throw IllegalStateException("Price has changed since your quote. Please request a new quote.")
            }
        }

        val isCod = paymentMethod == "COD"
        if (isCod && !quote.isCodAvailable) {
            throw IllegalArgumentException("COD_NOT_ELIGIBLE: ${quote.codRejectionReason ?: "Order total exceeds COD limit"}")
        }

        val reservedItems = mutableListOf<OrderItemRequest>()
        var couponReserved = false
        var savedOrderId: UUID? = null

        try {
            val orderItemsToSave = request.items.map { item ->
                decrementCatalogStock(item.offeringId, item.quantity).also { reservedItems.add(item) }
            }
            val initialStatus = if (isCod) OrderStatus.ACCEPTED else OrderStatus.PLACED
            val paymentStatus = if (isCod) "COD_PENDING" else "PENDING"
            val savedOrder = orderRepository.save(
                Order(
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
            )
            savedOrderId = savedOrder.orderId
            orderItemsToSave.forEach { item ->
                item.orderId = savedOrder.orderId!!
                orderItemRepository.save(item)
            }

            if (quote.couponCode != null) {
                val reservedDiscount = reserveCouponDiscount(
                    quote.couponCode,
                    quote.subtotal,
                    request.providerId,
                    activeCustomerId,
                    savedOrder.orderId!!
                )
                couponReserved = true
                if (reservedDiscount.compareTo(quote.couponDiscount) != 0) {
                    throw IllegalStateException("Coupon pricing changed before order placement. Please request a new quote.")
                }
            }

            logStatusChange(savedOrder.orderId!!, null, initialStatus, savedOrder.customerId, "Order placed successfully")
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
        } catch (error: Exception) {
            try {
                compensationService?.recordFailure(
                    orderId = savedOrderId,
                    customerId = activeCustomerId,
                    couponCode = if (couponReserved) request.couponCode else null,
                    items = reservedItems
                ) ?: throw IllegalStateException("Durable compensation service is unavailable")
            } catch (recordingFailure: Exception) {
                logger.error("CRITICAL: Failed to durably record order compensation", recordingFailure)
                throw IllegalStateException(
                    "Order failed and compensation could not be recorded safely",
                    recordingFailure
                ).also { it.addSuppressed(error) }
            }
            throw error
        }
    }

    fun confirmOrder(orderId: UUID, paymentId: UUID?): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        if (order.status == OrderStatus.ACCEPTED) return order
        if (order.status != OrderStatus.PLACED) {
            throw IllegalStateException("Order is not in PLACED state. Current state: ${order.status}")
        }

        val paymentIdToUse = paymentId ?: throw IllegalArgumentException("Payment ID is required to confirm order")
        try {
            val transaction = paymentModule.transaction(paymentIdToUse)
                ?: throw IllegalStateException("Payment transaction $paymentIdToUse not found")
            if (transaction.status != "SUCCESS") {
                throw IllegalStateException("Payment status is ${transaction.status}, but expected SUCCESS to confirm order")
            }
            if (transaction.amount.compareTo(order.totalAmount) != 0) {
                throw IllegalStateException(
                    "Payment amount ₹${transaction.amount} does not match order total ₹${order.totalAmount}"
                )
            }
        } catch (error: Exception) {
            throw IllegalStateException("Payment verification failed: ${error.message}", error)
        }

        order.couponCode?.let { redeemCouponReservation(it, order.customerId, order.orderId!!) }
        val oldStatus = order.status
        order.status = OrderStatus.ACCEPTED
        order.paymentId = paymentIdToUse
        order.paymentStatus = "SUCCESS"
        order.acceptedAt = Instant.now()
        val saved = orderRepository.save(order)
        logStatusChange(saved.orderId!!, oldStatus, OrderStatus.ACCEPTED, saved.customerId, "Order confirmed and paid")
        publishOrderStatusEvent(saved, oldStatus, OrderStatus.ACCEPTED, saved.customerId)
        return saved
    }

    fun updateOrderStatus(orderId: UUID, newStatus: OrderStatus, changedBy: UUID, note: String? = null): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        val oldStatus = order.status
        if (oldStatus == newStatus) return order
        if (oldStatus in setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED)) {
            throw IllegalStateException("Order in terminal state $oldStatus cannot transition to $newStatus")
        }
        if (shouldRestoreReservedStock(oldStatus, newStatus)) restoreOrderCatalogStock(orderId)
        order.status = newStatus

        when (newStatus) {
            OrderStatus.ACCEPTED -> order.acceptedAt = Instant.now()
            OrderStatus.ASSIGNED, OrderStatus.REASSIGNED -> order.captainId = changedBy
            OrderStatus.READY_FOR_PICKUP -> order.readyAt = Instant.now()
            OrderStatus.PICKED_UP -> order.picked_upAt = Instant.now()
            OrderStatus.DELIVERED -> {
                if (order.paymentMethod == "COD") {
                    order.couponCode?.let { redeemCouponReservation(it, order.customerId, order.orderId!!) }
                    order.paymentStatus = "COD_COLLECTED"
                }
                order.deliveredAt = Instant.now()
                generateInvoiceForOrder(order)
                notifyLoyaltyOrderDelivered(order)
            }
            OrderStatus.CANCELLED -> {
                order.cancelledAt = Instant.now()
                order.cancellationReason = note
            }
            else -> Unit
        }

        val updatedOrder = orderRepository.save(order)
        logStatusChange(orderId, oldStatus, newStatus, changedBy, note)
        publishOrderStatusEvent(updatedOrder, oldStatus, newStatus, changedBy)
        return updatedOrder
    }

    private fun publishOrderStatusEvent(order: Order, oldStatus: OrderStatus, newStatus: OrderStatus, actorId: UUID) {
        val event = OrderStatusChangedEvent(
            eventType = if (newStatus == OrderStatus.CANCELLED) "OrderCancelled" else "OrderStatusChanged",
            orderId = order.orderId!!,
            actorId = actorId,
            fromStatus = oldStatus.name,
            toStatus = newStatus.name,
            totalAmount = order.totalAmount,
            deliveryFee = order.deliveryFee,
            captainId = order.captainId,
            providerId = order.providerId,
            merchantOwnerUserId = fetchProviderOwnerUserId(order.providerId)
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "ORDER",
            aggregateId = order.orderId!!,
            eventType = event.eventType,
            eventPayload = event
        )
    }

    private fun logStatusChange(
        orderId: UUID,
        fromStatus: OrderStatus?,
        toStatus: OrderStatus,
        changedByUserId: UUID,
        note: String?
    ) {
        orderStatusHistoryRepository.save(
            OrderStatusHistory(
                orderId = orderId,
                fromStatus = fromStatus,
                toStatus = toStatus,
                changedByUserId = changedByUserId,
                note = note
            )
        )
    }

    @CircuitBreaker(name = "catalogService", fallbackMethod = "decrementCatalogStockFallback")
    @Retry(name = "catalogService")
    private fun decrementCatalogStock(offeringId: UUID, quantity: Int): OrderItem {
        require(quantity > 0) { "Quantity must be greater than zero" }
        val snapshot = catalogModule.reserveStock(
            StockMutationCommand(
                offeringId = offeringId,
                quantity = quantity,
                idempotencyKey = UUID.nameUUIDFromBytes("reserve:$offeringId:$quantity".toByteArray())
            )
        )
        val lineSubtotal = snapshot.price.multiply(BigDecimal(quantity))
        val lineGst = lineSubtotal.multiply(snapshot.gstRate).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
        return OrderItem(
            orderId = UUID.randomUUID(),
            offeringId = offeringId,
            offeringNameSnapshot = snapshot.name,
            unitPriceSnapshot = snapshot.price,
            quantity = quantity,
            lineTotal = lineSubtotal,
            gstAmount = lineGst
        )
    }

    @Suppress("unused")
    fun decrementCatalogStockFallback(offeringId: UUID, quantity: Int, error: Throwable): OrderItem {
        logger.error("Catalog module reservation failed (circuit breaker fallback active): {}", error.message)
        throw IllegalStateException("Catalog service is currently unavailable (circuit open). Please try again later.", error)
    }

    private fun restoreReservedCatalogStock(items: List<OrderItemRequest>) {
        items.asReversed().forEach { item ->
            try {
                catalogModule.restoreStock(
                    StockMutationCommand(
                        offeringId = item.offeringId,
                        quantity = item.quantity,
                        idempotencyKey = UUID.nameUUIDFromBytes(
                            "restore:${item.offeringId}:${item.quantity}".toByteArray()
                        )
                    )
                )
            } catch (error: Exception) {
                logger.error("WARNING: Failed to restore catalog stock for offering {}: {}", item.offeringId, error.message)
            }
        }
    }

    private fun restoreOrderCatalogStock(orderId: UUID) {
        restoreReservedCatalogStock(
            orderItemRepository.findByOrderId(orderId).map { OrderItemRequest(it.offeringId, it.quantity) }
        )
    }

    private fun shouldRestoreReservedStock(oldStatus: OrderStatus, newStatus: OrderStatus): Boolean {
        val releasingStatuses = setOf(OrderStatus.CANCELLED, OrderStatus.REJECTED)
        return newStatus in releasingStatuses && oldStatus !in releasingStatuses
    }

    fun getDisputeRefundMode(): String = systemConfigRepository.findById("dispute_refund_mode")
        .map { it.configValue }
        .orElse("MANUAL")

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

    fun createDisputeWithAuth(
        orderId: UUID,
        reason: String,
        callerId: UUID,
        callerRole: String?
    ): Dispute {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("Order with ID $orderId not found") }
        val isAdmin = callerRole.equals("ADMIN", ignoreCase = true)
        val isCustomer = order.customerId == callerId
        if (!isAdmin && !isCustomer) {
            throw OrderAccessDeniedException("Only the order customer or an administrator can create a dispute.")
        }
        val safeReason = reason.trim()
        require(safeReason.isNotBlank()) { "Dispute reason is required" }
        require(safeReason.length <= 2000) { "Dispute reason exceeds 2000 characters" }
        return disputeRepository.save(Dispute(orderId = orderId, status = "OPEN", reason = safeReason))
    }

    fun listDisputes(): List<Dispute> = disputeRepository.findAll()

    fun getInvoiceByOrderIdWithAuth(orderId: UUID, callerId: UUID, callerRole: String?): Invoice {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        assertCanAccessOrder(order, callerId, callerRole)
        return invoiceRepository.findByOrderId(orderId)
            .orElseThrow { NoSuchElementException("Invoice not found for order $orderId") }
    }

    fun resolveDispute(disputeId: UUID, decision: String, resolutionNotes: String?): Dispute {
        val dispute = disputeRepository.findById(disputeId)
            .orElseThrow { NoSuchElementException("Dispute not found for ID $disputeId") }
        if (dispute.status != "OPEN") throw IllegalStateException("Dispute is already resolved")
        dispute.status = decision
        dispute.resolutionNotes = resolutionNotes
        dispute.resolvedAt = Instant.now()
        val savedDispute = disputeRepository.save(dispute)
        if (decision == "RESOLVED" && getDisputeRefundMode() == "AUTOMATED") {
            triggerPaymentRefund(dispute.orderId)
        }
        return savedDispute
    }

    fun listSupportCases(): List<SupportCase> = supportCaseRepository.findAllByOrderByCreatedAtDesc()

    fun createSupportCase(
        title: String,
        detail: String,
        actionType: String,
        entityType: String?,
        entityId: UUID?,
        createdByUserId: UUID?
    ): SupportCase {
        require(title.isNotBlank()) { "Support case title is required" }
        require(detail.isNotBlank()) { "Support case detail is required" }
        val saved = supportCaseRepository.save(
            SupportCase(
                title = title.trim(),
                detail = detail.trim(),
                actionType = normalizeSupportActionType(actionType),
                entityType = entityType?.trim()?.uppercase()?.ifBlank { null },
                entityId = entityId,
                status = "OPEN",
                createdByUserId = createdByUserId
            )
        )
        publishSupportCaseEvent("SupportCaseOpened", saved)
        return saved
    }

    fun resolveSupportCase(supportCaseId: UUID, resolutionNotes: String?, actorId: UUID?): SupportCase {
        val supportCase = supportCaseRepository.findById(supportCaseId)
            .orElseThrow { NoSuchElementException("Support case not found for ID $supportCaseId") }
        if (supportCase.status != "OPEN") throw IllegalStateException("Support case is already resolved")
        supportCase.status = "RESOLVED"
        supportCase.resolutionNotes = resolutionNotes
        supportCase.resolvedAt = Instant.now()
        val saved = supportCaseRepository.save(supportCase)
        publishSupportCaseEvent("SupportCaseResolved", saved, actorId)
        return saved
    }

    private fun normalizeSupportActionType(actionType: String): String {
        val normalized = actionType.trim().uppercase()
        val allowed = setOf("INFO_REQUEST", "REFUND_ESCALATION", "PAYOUT_CLAIM_REVIEW", "CUSTOMER_CALLBACK", "GENERAL")
        if (normalized !in allowed) {
            throw IllegalArgumentException("Invalid support action type. Allowed: ${allowed.joinToString(", ")}")
        }
        return normalized
    }

    private fun publishSupportCaseEvent(
        eventType: String,
        supportCase: SupportCase,
        actorId: UUID? = supportCase.createdByUserId
    ) {
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
            paymentModule.refundOrder(orderId)
            logger.info("Dispute System: Triggered automated refund for order {}", orderId)
        } catch (error: Exception) {
            logger.error("WARNING: Failed to invoke payment refund for order {}: {}", orderId, error.message)
        }
    }

    private fun generateInvoiceForOrder(order: Order) {
        if (!invoiceRepository.findByOrderId(order.orderId!!).isPresent) {
            val invoiceNumber = "INV-${java.time.LocalDate.now().year}-${order.orderId.toString().substring(0, 8).uppercase()}"
            invoiceRepository.save(
                Invoice(
                    orderId = order.orderId!!,
                    invoiceNumber = invoiceNumber,
                    subtotalAmount = order.subtotalAmount,
                    taxAmount = order.taxAmount,
                    totalAmount = order.totalAmount
                )
            )
            logger.info("Invoicing: Generated invoice {} for order {}", invoiceNumber, order.orderId)
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
        val allowed = normalizedRole == "ADMIN" ||
            (normalizedRole == "MERCHANT" && fetchProviderOwnerUserId(providerId) == callerId)
        if (!allowed) throw OrderAccessDeniedException("Access denied to provider orders.")
        return orderRepository.findByProviderId(providerId)
    }

    fun getCustomerOrderSummariesWithAuth(
        customerId: UUID,
        callerId: UUID,
        callerRole: String?
    ): List<CustomerOrderSummary> {
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
        val allowed = when (callerRole?.uppercase()) {
            "ADMIN" -> true
            "MERCHANT" -> fetchProviderOwnerUserId(order.providerId) == callerId &&
                newStatus in setOf(
                    OrderStatus.ACCEPTED,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.CANCELLED,
                    OrderStatus.REJECTED
                )
            "CAPTAIN" -> order.captainId == callerId &&
                newStatus in setOf(OrderStatus.PICKED_UP, OrderStatus.DELIVERED)
            else -> false
        }
        if (!allowed) throw OrderAccessDeniedException("Access denied for this order status transition.")
        return updateOrderStatus(orderId, newStatus, callerId, note)
    }

    fun confirmOrderWithAuth(orderId: UUID, paymentId: UUID?, callerId: UUID, callerRole: String?): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        assertCanAccessOrder(order, callerId, callerRole)
        return confirmOrder(orderId, paymentId)
    }

    fun cancelOrder(orderId: UUID, callerId: UUID, callerRole: String?, reason: String?): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        assertCanAccessOrder(order, callerId, callerRole)
        if (order.status !in setOf(OrderStatus.PLACED, OrderStatus.ACCEPTED)) {
            throw IllegalStateException("Order in status ${order.status} cannot be cancelled.")
        }
        order.couponCode?.let { releaseCouponReservation(it, order.customerId, order.orderId!!) }
        return updateOrderStatus(orderId, OrderStatus.CANCELLED, callerId, reason ?: "Cancelled by user")
    }

    fun revalidateReorder(orderId: UUID, callerId: UUID, callerRole: String?): ReorderValidationResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        assertCanAccessOrder(order, callerId, callerRole)

        var allAvailable = true
        val validatedItems = orderItemRepository.findByOrderId(orderId).map { item ->
            var available = true
            var message: String? = null
            var currentPrice = item.unitPriceSnapshot
            var name = item.offeringNameSnapshot
            try {
                val offering = catalogModule.offering(item.offeringId)
                currentPrice = offering.price
                name = offering.name
                val stock = offering.stockQuantity ?: 0
                if (offering.status != "ACTIVE") {
                    available = false
                    message = "Offering is no longer active"
                } else if (stock < item.quantity) {
                    available = false
                    message = "Insufficient stock ($stock available)"
                }
            } catch (error: Exception) {
                logger.warn("Reorder offering check failed for {}: {}", item.offeringId, error.message)
                available = false
                message = "Offering could not be verified"
            }
            if (!available) allAvailable = false
            ReorderValidationItem(
                offeringId = item.offeringId,
                offeringName = name,
                unitPrice = currentPrice,
                quantity = item.quantity,
                isAvailable = available,
                message = message
            )
        }

        return ReorderValidationResponse(
            originalOrderId = orderId,
            providerId = order.providerId,
            isProviderServiceable = true,
            items = validatedItems,
            canReorder = allAvailable
        )
    }

    private fun assertCanAccessOrder(order: Order, callerId: UUID, callerRole: String?) {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isCustomer = order.customerId == callerId
        val isProviderOwner = callerRole?.uppercase() == "MERCHANT" &&
            fetchProviderOwnerUserId(order.providerId) == callerId
        if (!isAdmin && !isCustomer && !isProviderOwner) {
            throw OrderAccessDeniedException("Access denied to order data.")
        }
    }

    private fun assertCanAccessCustomerOrders(targetCustomerId: UUID, callerId: UUID, callerRole: String?) {
        if (callerRole?.uppercase() != "ADMIN" && targetCustomerId != callerId) {
            throw OrderAccessDeniedException("Access denied to customer order history.")
        }
    }

    fun getCustomerOrderSummaries(customerId: UUID): List<CustomerOrderSummary> =
        orderRepository.findByCustomerId(customerId).map { order ->
            CustomerOrderSummary(
                orderId = order.orderId!!,
                providerId = order.providerId,
                status = order.status,
                flowStep = mapFlowStep(order.status),
                totalAmount = order.totalAmount,
                placedAt = order.placedAt,
                items = orderItemRepository.findByOrderId(order.orderId!!).map { it.offeringNameSnapshot },
                statusHistory = orderStatusHistoryRepository.findByOrderId(order.orderId!!).map {
                    OrderStatusHistoryEntry(it.fromStatus, it.toStatus, it.changedAt, it.note)
                }
            )
        }.sortedByDescending { it.placedAt }

    private fun fetchProviderOwnerUserId(providerId: UUID): UUID? = try {
        providerModule.ownerUserId(providerId)
    } catch (error: Exception) {
        logger.warn("Could not resolve provider owner for {}: {}", providerId, error.message)
        null
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

    private fun fetchOfferingSnapshot(offeringId: UUID) = try {
        catalogModule.offering(offeringId)
    } catch (error: Exception) {
        logger.warn("Catalog lookup failed for {}: {}", offeringId, error.message)
        throw IllegalStateException("Catalog service is unavailable. Please try checkout again.", error)
    }

    private fun validateCouponDiscount(code: String, subtotal: BigDecimal, providerId: UUID): BigDecimal {
        val promo = try {
            paymentModule.promotionTerms(code, subtotal, providerId)
        } catch (error: Exception) {
            logger.info("Coupon validation rejected code {}: {}", code, error.message)
            throw IllegalArgumentException("Coupon is invalid, expired, or not applicable to this order")
        }
        return when (promo.discountType.uppercase()) {
            "PERCENTAGE" -> {
                val raw = subtotal.multiply(promo.discountValue)
                    .divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                promo.maxDiscountAmount?.let(raw::min) ?: raw
            }
            "FLAT" -> promo.discountValue.min(subtotal)
            else -> throw IllegalStateException("Payment module returned an invalid coupon type")
        }
    }

    private fun reserveCouponDiscount(
        code: String,
        subtotal: BigDecimal,
        providerId: UUID,
        userId: UUID,
        orderId: UUID
    ): BigDecimal = try {
        paymentModule.reserveCoupon(
            CouponReservationCommand(
                code = code,
                orderValue = subtotal,
                providerId = providerId,
                userId = userId,
                orderId = orderId
            )
        )
    } catch (error: Exception) {
        logger.info("Coupon reservation rejected code {} for order {}: {}", code, orderId, error.message)
        throw IllegalArgumentException("Coupon could not be reserved. Please request a new quote.")
    }

    private fun releaseCouponReservation(code: String, userId: UUID, orderId: UUID) {
        try {
            paymentModule.releaseCoupon(code, userId, orderId)
        } catch (error: Exception) {
            logger.error("Failed to release coupon reservation for order {}: {}", orderId, error.message)
        }
    }

    private fun redeemCouponReservation(code: String, userId: UUID, orderId: UUID) {
        paymentModule.redeemCoupon(code, userId, orderId)
    }

    private fun checkCodEligibility(amount: BigDecimal, city: String?, providerId: UUID?): Pair<Boolean, String?> =
        try {
            paymentModule.codEligibility(amount, city, providerId).let { it.eligible to it.reason }
        } catch (error: Exception) {
            logger.warn("COD eligibility check failed: {}", error.message)
            false to "Cash on delivery is temporarily unavailable"
        }

    private fun validateItems(items: List<OrderItemRequest>, subject: String) {
        if (items.isEmpty()) throw IllegalArgumentException("$subject must contain at least one item")
        if (items.size > 50) throw IllegalArgumentException("$subject cannot contain more than 50 line items")
        if (items.any { it.quantity !in 1..99 }) throw IllegalArgumentException("Item quantities must be between 1 and 99")
        if (items.map { it.offeringId }.distinct().size != items.size) {
            throw IllegalArgumentException("Duplicate offering IDs are not allowed")
        }
    }

    private fun normalizePaymentMethod(paymentMethod: String?): String {
        val normalized = paymentMethod?.trim()?.uppercase() ?: "CARD"
        if (normalized !in setOf("CARD", "UPI", "COD")) throw IllegalArgumentException("Unsupported payment method")
        return normalized
    }

    private fun validateServiceability(city: String?, latitude: Double?, longitude: Double?) {
        if ((latitude == null) != (longitude == null)) {
            throw IllegalArgumentException("Latitude and longitude must be provided together")
        }
        if (city.isNullOrBlank() && latitude == null) return
        val result = try {
            discoveryModule.checkServiceability(city, latitude, longitude)
        } catch (error: Exception) {
            logger.warn("Serviceability lookup failed: {}", error.message)
            throw IllegalStateException("Delivery serviceability could not be verified. Please try again.", error)
        }
        if (!result.serviceable) {
            throw IllegalArgumentException("UNSERVICEABLE_REGION: ${result.reason ?: "Location is outside active service regions"}")
        }
    }

    private fun notifyLoyaltyOrderDelivered(order: Order) {
        try {
            paymentModule.recordOrderDelivered(order.orderId!!, order.customerId, order.providerId, order.totalAmount)
        } catch (error: Exception) {
            logger.warn("Could not notify loyalty module for delivered order {}: {}", order.orderId, error.message)
        }
    }

    @Suppress("unused")
    private fun notifyLoyaltyOrderRefunded(order: Order) {
        try {
            paymentModule.recordOrderRefunded(order.orderId!!, order.customerId, order.providerId)
        } catch (error: Exception) {
            logger.warn("Could not notify loyalty module for refunded order {}: {}", order.orderId, error.message)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OrderService::class.java)
    }
}
