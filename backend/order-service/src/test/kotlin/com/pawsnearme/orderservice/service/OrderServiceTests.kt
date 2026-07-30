package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.*
import com.pawsnearme.orderservice.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderServiceTests {

    private lateinit var orderRepository: OrderRepository
    private lateinit var orderItemRepository: OrderItemRepository
    private lateinit var orderStatusHistoryRepository: OrderStatusHistoryRepository
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    private lateinit var systemConfigRepository: SystemConfigRepository
    private lateinit var disputeRepository: DisputeRepository
    private lateinit var invoiceRepository: InvoiceRepository
    private lateinit var supportCaseRepository: SupportCaseRepository
    private lateinit var restTemplate: RestTemplate
    private lateinit var outboxService: com.pawsnearme.common.outbox.OutboxService
    private lateinit var service: OrderService

    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        orderRepository = mock()
        orderItemRepository = mock()
        orderStatusHistoryRepository = mock()
        kafkaTemplate = mock()
        systemConfigRepository = mock()
        disputeRepository = mock()
        invoiceRepository = mock()
        supportCaseRepository = mock()
        restTemplate = mock()
        outboxService = mock()

        service = OrderService(
            orderRepository = orderRepository,
            orderItemRepository = orderItemRepository,
            orderStatusHistoryRepository = orderStatusHistoryRepository,
            kafkaTemplate = kafkaTemplate,
            systemConfigRepository = systemConfigRepository,
            disputeRepository = disputeRepository,
            invoiceRepository = invoiceRepository,
            supportCaseRepository = supportCaseRepository,
            outboxService = outboxService,
            catalogServiceUrl = "http://localhost:8082",
            paymentServiceUrl = "http://localhost:8090",
            providerServiceUrl = "http://localhost:8081",
            discoveryServiceUrl = "http://localhost:8083",
            restTemplate = restTemplate
        )

    }

    private fun savedOrder(status: OrderStatus) = Order(
        orderId = UUID.randomUUID(),
        customerId = customerId,
        providerId = providerId,
        deliveryAddressId = UUID.randomUUID(),
        status = status,
        subtotalAmount = BigDecimal("500.00"),
        totalAmount = BigDecimal("500.00")
    )

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    fun `createOrder - rejects order with empty items`() {
        val request = CreateOrderRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
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
        whenever(orderRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Order>(0) }

        val result = service.updateOrderStatus(order.orderId!!, OrderStatus.ACCEPTED, customerId)

        assertEquals(OrderStatus.ACCEPTED, result.status)
        assertNotNull(result.acceptedAt)
        verify(orderRepository).save(any())
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("ORDER"),
            aggregateId = eq(order.orderId!!),
            eventType = eq("OrderStatusChanged"),
            eventPayload = any()
        )
    }

    @Test
    fun `updateOrderStatus - sets readyAt when transitioning to READY_FOR_PICKUP`() {
        val order = savedOrder(OrderStatus.ACCEPTED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Order>(0) }

        val result = service.updateOrderStatus(order.orderId!!, OrderStatus.READY_FOR_PICKUP, customerId)

        assertEquals(OrderStatus.READY_FOR_PICKUP, result.status)
        assertNotNull(result.readyAt)
        verify(orderRepository).save(any())
    }

    @Test
    fun `updateOrderStatus - DELIVERED - sets deliveredAt and generates invoice`() {
        val order = savedOrder(OrderStatus.PICKED_UP)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Order>(0) }
        whenever(invoiceRepository.findByOrderId(order.orderId!!)).thenReturn(java.util.Optional.empty())

        val result = service.updateOrderStatus(order.orderId!!, OrderStatus.DELIVERED, customerId)

        assertEquals(OrderStatus.DELIVERED, result.status)
        assertNotNull(result.deliveredAt)
        verify(invoiceRepository).save(check<Invoice> {
            assertEquals(order.orderId, it.orderId)
            assertEquals(order.subtotalAmount, it.subtotalAmount)
            assertEquals(order.taxAmount, it.taxAmount)
            assertEquals(order.totalAmount, it.totalAmount)
            assertTrue(it.invoiceNumber.startsWith("INV-"))
        })
    }

    @Test
    fun `updateOrderStatus - DELIVERED - does not duplicate invoice if exists`() {
        val order = savedOrder(OrderStatus.PICKED_UP)
        val invoice = Invoice(
            orderId = order.orderId!!,
            invoiceNumber = "INV-2026-ABCD",
            subtotalAmount = BigDecimal("500.00"),
            taxAmount = BigDecimal("90.00"),
            totalAmount = BigDecimal("590.00")
        )
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Order>(0) }
        whenever(invoiceRepository.findByOrderId(order.orderId!!)).thenReturn(java.util.Optional.of(invoice))

        val result = service.updateOrderStatus(order.orderId!!, OrderStatus.DELIVERED, customerId)

        assertEquals(OrderStatus.DELIVERED, result.status)
        verify(invoiceRepository, never()).save(any())
    }

    @Test
    fun `updateOrderStatus - CANCELLED - restores reserved stock`() {
        val order = savedOrder(OrderStatus.PLACED)
        val items = listOf(OrderItem(orderId = order.orderId!!, offeringId = UUID.randomUUID(), offeringNameSnapshot = "Item 1", unitPriceSnapshot = BigDecimal("100.00"), quantity = 2, lineTotal = BigDecimal("200.00")))
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Order>(0) }
        whenever(orderItemRepository.findByOrderId(order.orderId!!)).thenReturn(items)
        whenever(restTemplate.exchange(
            any<String>(),
            eq(org.springframework.http.HttpMethod.PUT),
            anyOrNull(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(emptyMap<String, Any>()))

        val result = service.updateOrderStatus(order.orderId!!, OrderStatus.CANCELLED, customerId, "customer request")

        assertEquals(OrderStatus.CANCELLED, result.status)
        assertNotNull(result.cancelledAt)
        assertEquals("customer request", result.cancellationReason)
        verify(restTemplate).exchange(
            eq("http://localhost:8082/api/v1/catalog/offerings/${items[0].offeringId}/restore-stock?quantity=2"),
            eq(org.springframework.http.HttpMethod.PUT),
            anyOrNull(),
            eq(Map::class.java)
        )
    }

    // ── support cases ─────────────────────────────────────────────────────────

    @Test
    fun `createSupportCase - creates support case and publishes event`() {
        val actorId = UUID.randomUUID()
        whenever(supportCaseRepository.save(any())).thenAnswer { invocation ->
            val supportCase = invocation.getArgument<SupportCase>(0)
            supportCase.also { it.supportCaseId = it.supportCaseId ?: UUID.randomUUID() }
        }
        whenever(kafkaTemplate.send(any<String>(), any(), any())).thenReturn(mock())

        val result = service.createSupportCase(
            title = "Escalate delayed refund",
            detail = "Customer has waited 5 days after dispute approval.",
            actionType = "REFUND_ESCALATION",
            entityType = "ORDER",
            entityId = UUID.randomUUID(),
            createdByUserId = actorId
        )

        assertEquals("Escalate delayed refund", result.title)
        assertEquals("REFUND_ESCALATION", result.actionType)
        assertEquals("OPEN", result.status)
        assertEquals(actorId, result.createdByUserId)
        verify(supportCaseRepository).save(any())
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("SUPPORT"),
            aggregateId = eq(result.supportCaseId!!),
            eventType = eq("SupportCaseOpened"),
            eventPayload = any()
        )
    }

    @Test
    fun `createSupportCase - rejects invalid action type`() {
        val ex = assertThrows<IllegalArgumentException> {
            service.createSupportCase(
                title = "Unknown action",
                detail = "Invalid action",
                actionType = "RANDOM_ACTION",
                entityType = null,
                entityId = null,
                createdByUserId = null
            )
        }

        assertTrue(ex.message!!.contains("Invalid support action type"))
        verify(supportCaseRepository, never()).save(any())
    }

    @Test
    fun `resolveSupportCase - marks case resolved and publishes event`() {
        val supportCaseId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val supportCase = SupportCase(
            supportCaseId = supportCaseId,
            title = "Review captain payout claim",
            detail = "Captain says earnings were missed.",
            actionType = "PAYOUT_CLAIM_REVIEW",
            status = "OPEN"
        )
        whenever(supportCaseRepository.findById(supportCaseId)).thenReturn(java.util.Optional.of(supportCase))
        whenever(supportCaseRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<SupportCase>(0) }
        whenever(kafkaTemplate.send(any<String>(), any(), any())).thenReturn(mock())

        val result = service.resolveSupportCase(supportCaseId, "Payout adjusted", actorId)

        assertEquals("RESOLVED", result.status)
        assertEquals("Payout adjusted", result.resolutionNotes)
        assertNotNull(result.resolvedAt)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("SUPPORT"),
            aggregateId = eq(supportCaseId),
            eventType = eq("SupportCaseResolved"),
            eventPayload = any()
        )
    }

    // ── confirmOrder ──────────────────────────────────────────────────────────

    @Test
    fun `confirmOrder - success confirms order, sets status ACCEPTED and paymentId`() {
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("500.00"),
            totalAmount = BigDecimal("500.00")
        )
        whenever(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { invocation -> invocation.getArgument<Order>(0) }
        
        val paymentResponse = mapOf(
            "status" to "SUCCESS",
            "amount" to 500.0
        )
        whenever(restTemplate.exchange(
            eq("http://localhost:8090/api/v1/payments/transactions/$paymentId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(paymentResponse))

        val saved = service.confirmOrder(orderId, paymentId)

        assertEquals(OrderStatus.ACCEPTED, saved.status)
        assertEquals(paymentId, saved.paymentId)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("ORDER"),
            aggregateId = eq(orderId),
            eventType = eq("OrderStatusChanged"),
            eventPayload = any()
        )
    }

    @Test
    fun `confirmOrder - payment status not SUCCESS - throws`() {
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("500.00"),
            totalAmount = BigDecimal("500.00")
        )
        whenever(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order))
        
        val paymentResponse = mapOf(
            "status" to "FAILED",
            "amount" to 500.0
        )
        whenever(restTemplate.exchange(
            eq("http://localhost:8090/api/v1/payments/transactions/$paymentId"),
            eq(org.springframework.http.HttpMethod.GET),
            any<org.springframework.http.HttpEntity<Any>>(),
            eq(Map::class.java)
        )).thenReturn(org.springframework.http.ResponseEntity.ok(paymentResponse))

        val ex = assertThrows<IllegalStateException> {
            service.confirmOrder(orderId, paymentId)
        }
        assertTrue(ex.message!!.contains("expected SUCCESS"))
    }
}
