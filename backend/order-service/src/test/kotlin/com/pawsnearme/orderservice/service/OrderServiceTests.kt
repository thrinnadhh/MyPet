package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.UUID

class OrderServiceTests {

    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val systemConfigRepository: SystemConfigRepository = mock()
    private val disputeRepository: DisputeRepository = mock()
    private val invoiceRepository: InvoiceRepository = mock()

    private val service = OrderService(
        orderRepository,
        orderItemRepository,
        orderStatusHistoryRepository,
        kafkaTemplate,
        systemConfigRepository,
        disputeRepository,
        invoiceRepository,
        "http://localhost:8082",
        "http://localhost:8090"
    )

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val addressId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        whenever(orderRepository.save(any())).thenAnswer { invocation ->
            val order = invocation.getArgument<Order>(0)
            order.also { it.orderId = it.orderId ?: UUID.randomUUID() }
        }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { invocation ->
            invocation.getArgument<List<OrderItem>>(0)
        }
        whenever(orderStatusHistoryRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<OrderStatusHistory>(0)
        }
    }

    private fun savedOrder(status: OrderStatus) = Order(
        customerId = customerId,
        providerId = providerId,
        deliveryAddressId = addressId,
        status = status,
        subtotalAmount = BigDecimal("100.00"),
        totalAmount = BigDecimal("100.00")
    ).also { it.orderId = UUID.randomUUID() }

    // ── createOrder — empty items guard ────────────────────────────────────────

    @Test
    fun `createOrder - empty items list - throws IllegalArgumentException`() {
        val request = CreateOrderRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = addressId,
            items = emptyList()
        )
        val ex = assertThrows<IllegalArgumentException> { service.createOrder(request) }
        assertTrue(ex.message!!.contains("at least one item"))
    }

    // ── decrementCatalogStockFallback ─────────────────────────────────────────

    @Test
    fun `decrementCatalogStockFallback - throws with circuit open message`() {
        val offeringId = UUID.randomUUID()
        val ex = assertThrows<IllegalStateException> {
            service.decrementCatalogStockFallback(offeringId, 1, "http://localhost", RuntimeException("timeout"))
        }
        assertTrue(ex.message!!.contains("circuit open"))
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    fun `updateOrderStatus - order not found - throws NoSuchElementException`() {
        val orderId = UUID.randomUUID()
        whenever(orderRepository.findById(orderId)).thenReturn(java.util.Optional.empty())

        assertThrows<NoSuchElementException> {
            service.updateOrderStatus(orderId, OrderStatus.ACCEPTED, customerId)
        }
    }

    @Test
    fun `updateOrderStatus - sets acceptedAt when transitioning to ACCEPTED`() {
        val order = savedOrder(OrderStatus.PLACED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(kafkaTemplate.send(any<String>(), any(), any())).thenReturn(mock())

        service.updateOrderStatus(order.orderId!!, OrderStatus.ACCEPTED, customerId)

        assertNotNull(order.acceptedAt)
        assertEquals(OrderStatus.ACCEPTED, order.status)
    }

    @Test
    fun `updateOrderStatus - publishes event id and actor id`() {
        val order = savedOrder(OrderStatus.PLACED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(kafkaTemplate.send(any<String>(), any(), any())).thenReturn(mock())

        service.updateOrderStatus(order.orderId!!, OrderStatus.CANCELLED, customerId, "customer request")

        argumentCaptor<Any>().apply {
            verify(kafkaTemplate).send(eq("orders.events"), eq(order.orderId.toString()), capture())
            val event = firstValue as OrderStatusChangedEvent
            assertNotNull(event.eventId)
            assertEquals("OrderCancelled", event.eventType)
            assertEquals(customerId, event.actorId)
            assertEquals(order.orderId, event.orderId)
        }
    }

    @Test
    fun `updateOrderStatus - sets deliveredAt when transitioning to DELIVERED`() {
        val order = savedOrder(OrderStatus.PICKED_UP)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(kafkaTemplate.send(any<String>(), any(), any())).thenReturn(mock())

        service.updateOrderStatus(order.orderId!!, OrderStatus.DELIVERED, customerId)

        assertNotNull(order.deliveredAt)
        assertEquals(OrderStatus.DELIVERED, order.status)
    }

    // ── Invoicing & Disputes — Sprint 9 ──────────────────────────────────────

    @Test
    fun `updateOrderStatus - DELIVERED - generates invoice when not present`() {
        val order = savedOrder(OrderStatus.PICKED_UP).apply {
            subtotalAmount = BigDecimal("100.00")
            totalAmount = BigDecimal("100.00")
        }
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(invoiceRepository.findByOrderId(order.orderId!!)).thenReturn(java.util.Optional.empty())

        service.updateOrderStatus(order.orderId!!, OrderStatus.DELIVERED, customerId)

        verify(invoiceRepository).save(argThat {
            assertEquals(order.orderId, orderId)
            assertEquals(BigDecimal("100.00"), subtotalAmount)
            assertEquals(BigDecimal("18.00"), taxAmount) // 18% of 100
            assertEquals(BigDecimal("118.00"), totalAmount) // 100 + 18
            invoiceNumber.startsWith("INV-")
        })
    }

    @Test
    fun `resolveDispute - MANUAL mode - resolves dispute and does not trigger refund`() {
        val disputeId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val dispute = Dispute(disputeId, orderId, "OPEN", "Wrong size")

        whenever(disputeRepository.findById(disputeId)).thenReturn(java.util.Optional.of(dispute))
        whenever(disputeRepository.save(any())).thenReturn(dispute)
        whenever(systemConfigRepository.findById("dispute_refund_mode")).thenReturn(java.util.Optional.of(SystemConfig("dispute_refund_mode", "MANUAL")))

        val result = service.resolveDispute(disputeId, "RESOLVED", "Refund approved manually")

        assertEquals("RESOLVED", result.status)
        assertEquals("Refund approved manually", result.resolutionNotes)
        assertNotNull(result.resolvedAt)
        verify(disputeRepository).save(any())
    }
}
