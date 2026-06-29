package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.repository.*
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    val orderId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val totalAmount: BigDecimal,
    val timestamp: Instant = Instant.now()
)

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}")
    private val catalogServiceUrl: String
) {
    private val restTemplate = RestTemplate()

    fun createOrder(request: CreateOrderRequest): Order {
        if (request.items.isEmpty()) {
            throw IllegalArgumentException("Order must contain at least one item")
        }

        var subtotal = BigDecimal.ZERO
        val orderItemsToSave = mutableListOf<OrderItem>()

        // 1. Validate offerings and decrement stock in Catalog Service (guarded by circuit breaker)
        for (item in request.items) {
            val cartEntry = decrementCatalogStock(item.offeringId, item.quantity, catalogServiceUrl)
            subtotal = subtotal.add(cartEntry.lineTotal)
            orderItemsToSave.add(cartEntry)
        }

        val total = subtotal.add(request.deliveryFee).subtract(request.discountAmount)

        // 2. Create and Save the Order
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

        // 3. Save Order Items with the persistent Order ID
        for (item in orderItemsToSave) {
            item.orderId = savedOrder.orderId!!
            orderItemRepository.save(item)
        }

        // 4. Log initial history
        logStatusChange(savedOrder.orderId!!, null, OrderStatus.PLACED, savedOrder.customerId, "Order placed successfully")

        // 5. Publish Order Placed Event to Kafka
        try {
            val event = OrderPlacedEvent(
                orderId = savedOrder.orderId!!,
                customerId = savedOrder.customerId,
                providerId = savedOrder.providerId,
                totalAmount = savedOrder.totalAmount
            )
            kafkaTemplate.send("orders.events", savedOrder.orderId.toString(), event)
        } catch (e: Exception) {
            println("WARNING: Failed to publish Kafka OrderPlaced event: ${e.message}")
        }

        return savedOrder
    }

    fun updateOrderStatus(orderId: UUID, newStatus: OrderStatus, changedBy: UUID, note: String? = null): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        
        val oldStatus = order.status
        order.status = newStatus
        
        when (newStatus) {
            OrderStatus.ACCEPTED -> order.acceptedAt = Instant.now()
            OrderStatus.ASSIGNED, OrderStatus.REASSIGNED -> order.captainId = changedBy
            OrderStatus.READY_FOR_PICKUP -> order.readyAt = Instant.now()
            OrderStatus.PICKED_UP -> order.picked_upAt = Instant.now()
            OrderStatus.DELIVERED -> order.deliveredAt = Instant.now()
            OrderStatus.CANCELLED -> {
                order.cancelledAt = Instant.now()
                order.cancellationReason = note
            }
            else -> {}
        }
        
        val updatedOrder = orderRepository.save(order)
        logStatusChange(orderId, oldStatus, newStatus, changedBy, note)

        // Publish status changed event to Kafka
        try {
            val event = mutableMapOf(
                "orderId" to orderId.toString(),
                "fromStatus" to oldStatus.name,
                "toStatus" to newStatus.name,
                "timestamp" to Instant.now().toString(),
                "totalAmount" to updatedOrder.totalAmount.toString(),
                "deliveryFee" to updatedOrder.deliveryFee.toString()
            )
            if (updatedOrder.captainId != null) {
                event["captainId"] = updatedOrder.captainId.toString()
            }
            kafkaTemplate.send("orders.events", orderId.toString(), event)
        } catch (e: Exception) {
            println("WARNING: Failed to publish Kafka OrderStatusChanged event: ${e.message}")
        }

        return updatedOrder
    }

    private fun logStatusChange(orderId: UUID, from: OrderStatus?, to: OrderStatus, by: UUID, note: String?) {
        val history = OrderStatusHistory(
            orderId = orderId,
            fromStatus = from,
            toStatus = to,
            changedByUserId = by,
            note = note
        )
        orderStatusHistoryRepository.save(history)
    }

    // ── Downstream calls with circuit breakers ──────────────────────────────

    @CircuitBreaker(name = "catalog-service", fallbackMethod = "decrementCatalogStockFallback")
    @Retry(name = "catalog-service")
    fun decrementCatalogStock(offeringId: UUID, quantity: Int, baseUrl: String): OrderItem {
        val url = "$baseUrl/api/v1/catalog/offerings/$offeringId/decrement-stock?quantity=$quantity"
        val offering = restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.PUT,
            null,
            Map::class.java
        ).body ?: throw IllegalStateException("Catalog service: offering $offeringId not found")

        val priceDouble = offering["price"] as? Double ?: 0.0
        val unitPrice = BigDecimal.valueOf(priceDouble)
        val name = offering["name"] as? String ?: "Pet Product"
        val lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity.toLong()))
        return OrderItem(
            orderId = UUID.randomUUID(),
            offeringId = offeringId,
            offeringNameSnapshot = name,
            unitPriceSnapshot = unitPrice,
            quantity = quantity,
            lineTotal = lineTotal
        )
    }

    fun decrementCatalogStockFallback(offeringId: UUID, quantity: Int, baseUrl: String, ex: Throwable): OrderItem {
        logger.error("Catalog circuit breaker OPEN for offering $offeringId — ${ex.message}")
        throw IllegalStateException("Catalog service unavailable. Please retry in a moment. (circuit open)")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OrderService::class.java)
    }
}

