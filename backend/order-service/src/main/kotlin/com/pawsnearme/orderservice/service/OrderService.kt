package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.repository.*
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

        // 1. Validate offerings and decrement stock in Catalog Service
        for (item in request.items) {
            // Call Catalog Service to decrement stock
            val url = "$catalogServiceUrl/api/v1/catalog/offerings/${item.offeringId}/decrement-stock?quantity=${item.quantity}"
            try {
                // Returns the updated offering details containing price/name snapshots
                val offering = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.PUT,
                    null,
                    Map::class.java
                ).body ?: throw IllegalStateException("Failed to decrement stock: offering not found")
                
                val priceDouble = offering["price"] as? Double ?: 0.0
                val unitPrice = BigDecimal.valueOf(priceDouble)
                val name = offering["name"] as? String ?: "Pet Product"
                val lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))
                subtotal = subtotal.add(lineTotal)

                orderItemsToSave.add(
                    OrderItem(
                        orderId = UUID.randomUUID(), // Temporarily set, will override below
                        offeringId = item.offeringId,
                        offeringNameSnapshot = name,
                        unitPriceSnapshot = unitPrice,
                        quantity = item.quantity,
                        lineTotal = lineTotal
                    )
                )
            } catch (e: Exception) {
                throw IllegalStateException("Failed to validate stock or deduct inventory: ${e.message}", e)
            }
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
            val event = mapOf(
                "orderId" to orderId.toString(),
                "fromStatus" to oldStatus.name,
                "toStatus" to newStatus.name,
                "timestamp" to Instant.now().toString()
            )
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
}
