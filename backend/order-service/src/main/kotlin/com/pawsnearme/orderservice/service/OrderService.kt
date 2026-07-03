package com.pawsnearme.orderservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.repository.*
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderItemRequest(
    val offeringId: UUID,
    val quantity: Int
)

data class CreateOrderRequest(
    val customerId: UUID,
    val providerId: UUID,
    val deliveryAddressId: UUID,
    val items: List<OrderItemRequest>,
    val deliveryFee: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO
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
    @Value("\${gateway.trust.secret:}")
    private val gatewayTrustSecret: String = "",
    private val restTemplate: RestTemplate = RestTemplate()
) {
    fun createOrder(request: CreateOrderRequest): Order {
        if (request.items.isEmpty()) {
            throw IllegalArgumentException("Order must contain at least one item")
        }

        val reservedItems = mutableListOf<OrderItemRequest>()

        try {
            var subtotal = BigDecimal.ZERO
            val orderItemsToSave = mutableListOf<OrderItem>()

            for (item in request.items) {
                val cartEntry = decrementCatalogStock(item.offeringId, item.quantity, catalogServiceUrl)
                reservedItems.add(item)
                subtotal = subtotal.add(cartEntry.lineTotal)
                orderItemsToSave.add(cartEntry)
            }

            val total = subtotal.add(request.deliveryFee).subtract(request.discountAmount)

            val order = Order(
                customerId = request.customerId,
                providerId = request.providerId,
                deliveryAddressId = request.deliveryAddressId,
                status = OrderStatus.PLACED,
                subtotalAmount = subtotal,
                deliveryFee = request.deliveryFee,
                discountAmount = request.discountAmount,
                totalAmount = total
            )
            val savedOrder = orderRepository.save(order)

            for (item in orderItemsToSave) {
                item.orderId = savedOrder.orderId!!
                orderItemRepository.save(item)
            }

            logStatusChange(savedOrder.orderId!!, null, OrderStatus.PLACED, savedOrder.customerId, "Order placed successfully")

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
            val amountVal = (tx["amount"] as? Number)?.toDouble() ?: 0.0
            val expectedAmount = order.totalAmount.toDouble()

            if (status != "SUCCESS") {
                throw IllegalStateException("Payment status is $status, but expected SUCCESS to confirm order")
            }
            if (Math.abs(amountVal - expectedAmount) > 0.01) {
                throw IllegalStateException("Payment amount $amountVal does not match order total $expectedAmount")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Payment verification failed: ${e.message}", e)
        }

        val oldStatus = order.status
        order.status = OrderStatus.ACCEPTED
        order.paymentId = paymentIdToUse
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
                order.deliveredAt = Instant.now()
                generateInvoiceForOrder(order)
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
            val taxRate = BigDecimal("0.18")
            val tax = subtotal.multiply(taxRate).setScale(2, java.math.RoundingMode.HALF_UP)
            val total = subtotal.add(tax).setScale(2, java.math.RoundingMode.HALF_UP)

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

    private fun internalHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        if (gatewayTrustSecret.isNotBlank()) {
            headers.set("X-Internal-Gateway-Secret", gatewayTrustSecret)
        }
        return headers
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OrderService::class.java)
    }
}
