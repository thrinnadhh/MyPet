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
    private val merchantId = UUID.randomUUID()
    private val captainId = UUID.randomUUID()

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

    private fun savedOrder(
        status: OrderStatus,
        paymentStatus: PaymentStatus = PaymentStatus.COD_PENDING,
        paymentMethod: String = "COD"
    ) = Order(
        orderId = UUID.randomUUID(),
        customerId = customerId,
        providerId = providerId,
        deliveryAddressId = UUID.randomUUID(),
        status = status,
        subtotalAmount = BigDecimal("500.00"),
        totalAmount = BigDecimal("500.00"),
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus
    )

    @Test
    fun `createDisputeWithAuth rejects a different customer`() {
        val order = savedOrder(OrderStatus.DELIVERED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        assertThrows<OrderAccessDeniedException> {
            service.createDisputeWithAuth(order.orderId!!, "Not my order", UUID.randomUUID(), "CUSTOMER")
        }

        verify(disputeRepository, never()).save(any())
    }

    @Test
    fun `getInvoiceByOrderIdWithAuth rejects unrelated customer`() {
        val order = savedOrder(OrderStatus.DELIVERED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        assertThrows<OrderAccessDeniedException> {
            service.getInvoiceByOrderIdWithAuth(order.orderId!!, UUID.randomUUID(), "CUSTOMER")
        }

        verify(invoiceRepository, never()).findByOrderId(any())
    }

    @Test
    fun `createOrder rejects order with empty items`() {
        val request = CreateOrderRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            items = emptyList()
        )

        val ex = assertThrows<IllegalArgumentException> { service.createOrder(request) }
        assertTrue(ex.message!!.contains("at least one item"))
    }

    @Test
    fun `createOrder rejects online checkout while native payment flow is disabled`() {
        val request = CreateOrderRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            items = listOf(OrderItemRequest(UUID.randomUUID(), 1)),
            paymentMethod = "CARD"
        )

        val ex = assertThrows<IllegalStateException> { service.createOrder(request) }

        assertTrue(ex.message!!.contains("Online checkout"))
        verifyNoInteractions(restTemplate)
    }

    @Test
    fun `calculateQuote rejects invalid quantity before calling dependencies`() {
        val request = CheckoutQuoteRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            items = listOf(OrderItemRequest(UUID.randomUUID(), 0)),
            paymentMethod = "CARD"
        )

        val ex = assertThrows<IllegalArgumentException> { service.calculateQuote(request) }

        assertTrue(ex.message!!.contains("quantities"))
        verifyNoInteractions(restTemplate)
    }

    @Test
    fun `calculateQuote fails closed when catalog pricing is unavailable`() {
        val offeringId = UUID.randomUUID()
        whenever(
            restTemplate.exchange(
                eq("http://localhost:8082/api/v1/internal/catalog/offerings/$offeringId"),
                eq(org.springframework.http.HttpMethod.GET),
                any<org.springframework.http.HttpEntity<Any>>(),
                eq(Map::class.java)
            )
        ).thenThrow(org.springframework.web.client.RestClientException("catalog timeout"))

        val request = CheckoutQuoteRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            items = listOf(OrderItemRequest(offeringId, 1)),
            paymentMethod = "CARD"
        )

        val ex = assertThrows<IllegalStateException> { service.calculateQuote(request) }
        assertTrue(ex.message!!.contains("Catalog service is unavailable"))
    }

    @Test
    fun `calculateQuote validates coupon without reserving it`() {
        val offeringId = UUID.randomUUID()
        whenever(
            restTemplate.exchange(
                eq("http://localhost:8082/api/v1/internal/catalog/offerings/$offeringId"),
                eq(org.springframework.http.HttpMethod.GET),
                any<org.springframework.http.HttpEntity<Any>>(),
                eq(Map::class.java)
            )
        ).thenReturn(
            org.springframework.http.ResponseEntity.ok(
                mapOf(
                    "providerId" to providerId.toString(),
                    "price" to BigDecimal("200.00"),
                    "status" to "ACTIVE",
                    "stockQuantity" to 5
                )
            )
        )
        whenever(
            restTemplate.exchange(
                argThat<String> { contains("/api/v1/payments/promotions/validate") },
                eq(org.springframework.http.HttpMethod.GET),
                any<org.springframework.http.HttpEntity<Any>>(),
                eq(Map::class.java)
            )
        ).thenReturn(
            org.springframework.http.ResponseEntity.ok(
                mapOf(
                    "discountType" to "PERCENTAGE",
                    "discountValue" to BigDecimal("10.00"),
                    "maxDiscountAmount" to BigDecimal("50.00")
                )
            )
        )

        val quote = service.calculateQuote(
            CheckoutQuoteRequest(
                customerId = customerId,
                providerId = providerId,
                deliveryAddressId = UUID.randomUUID(),
                items = listOf(OrderItemRequest(offeringId, 1)),
                couponCode = " save10 ",
                paymentMethod = "CARD"
            )
        )

        assertEquals(BigDecimal("20.00"), quote.couponDiscount)
        assertEquals("SAVE10", quote.couponCode)
        verify(restTemplate, never()).postForEntity(any<String>(), any(), eq(Map::class.java))
    }

    @Test
    fun `decrementCatalogStockFallback throws with circuit open message`() {
        val offeringId = UUID.randomUUID()
        val ex = assertThrows<IllegalStateException> {
            service.decrementCatalogStockFallback(offeringId, 1, RuntimeException("timeout"))
        }
        assertTrue(ex.message!!.contains("circuit open"))
    }

    @Test
    fun `updateOrderStatus order not found throws`() {
        val orderId = UUID.randomUUID()
        whenever(orderRepository.findById(orderId)).thenReturn(java.util.Optional.empty())

        assertThrows<NoSuchElementException> {
            service.updateOrderStatus(orderId, OrderStatus.ACCEPTED, merchantId, OrderActor.MERCHANT)
        }
    }

    @Test
    fun `merchant accepts actionable PLACED order and acceptedAt is set`() {
        val order = savedOrder(OrderStatus.PLACED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.saveAndFlush(any())).thenAnswer { it.getArgument<Order>(0) }

        val result = service.updateOrderStatus(
            order.orderId!!,
            OrderStatus.ACCEPTED,
            merchantId,
            OrderActor.MERCHANT
        )

        assertEquals(OrderStatus.ACCEPTED, result.status)
        assertNotNull(result.acceptedAt)
        verify(orderRepository).saveAndFlush(any())
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("ORDER"),
            aggregateId = eq(order.orderId!!),
            eventType = eq("OrderStatusChanged"),
            eventPayload = any()
        )
    }

    @Test
    fun `merchant cannot accept unpaid online PLACED order`() {
        val order = savedOrder(OrderStatus.PLACED, PaymentStatus.PENDING, "CARD")
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        val ex = assertThrows<OrderTransitionConflictException> {
            service.updateOrderStatus(
                order.orderId!!,
                OrderStatus.ACCEPTED,
                merchantId,
                OrderActor.MERCHANT
            )
        }

        assertTrue(ex.message!!.contains("payment status"))
        verify(orderRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `PLACED to READY_FOR_PICKUP is rejected`() {
        val order = savedOrder(OrderStatus.PLACED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        assertThrows<OrderTransitionConflictException> {
            service.updateOrderStatus(
                order.orderId!!,
                OrderStatus.READY_FOR_PICKUP,
                merchantId,
                OrderActor.MERCHANT
            )
        }
        verify(orderRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `ACCEPTED to READY_FOR_PICKUP without PREPARING is rejected`() {
        val order = savedOrder(OrderStatus.ACCEPTED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        assertThrows<OrderTransitionConflictException> {
            service.updateOrderStatus(
                order.orderId!!,
                OrderStatus.READY_FOR_PICKUP,
                merchantId,
                OrderActor.MERCHANT
            )
        }
        verify(orderRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `ACCEPTED to PREPARING then PREPARING to READY_FOR_PICKUP succeeds`() {
        val order = savedOrder(OrderStatus.ACCEPTED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.saveAndFlush(any())).thenAnswer { it.getArgument<Order>(0) }

        val preparing = service.updateOrderStatus(
            order.orderId!!,
            OrderStatus.PREPARING,
            merchantId,
            OrderActor.MERCHANT
        )
        assertEquals(OrderStatus.PREPARING, preparing.status)

        val ready = service.updateOrderStatus(
            order.orderId!!,
            OrderStatus.READY_FOR_PICKUP,
            merchantId,
            OrderActor.MERCHANT
        )
        assertEquals(OrderStatus.READY_FOR_PICKUP, ready.status)
        assertNotNull(ready.readyAt)
    }

    @Test
    fun `ASSIGNED to DELIVERED is rejected`() {
        val order = savedOrder(OrderStatus.ASSIGNED)
        order.captainId = captainId
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        assertThrows<OrderTransitionConflictException> {
            service.updateOrderStatus(
                order.orderId!!,
                OrderStatus.DELIVERED,
                captainId,
                OrderActor.CAPTAIN
            )
        }
    }

    @Test
    fun `PICKED_UP to DELIVERED sets deliveredAt and generates invoice`() {
        val order = savedOrder(OrderStatus.PICKED_UP, PaymentStatus.SUCCESS, "CARD")
        order.captainId = captainId
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.saveAndFlush(any())).thenAnswer { it.getArgument<Order>(0) }
        whenever(invoiceRepository.findByOrderId(order.orderId!!)).thenReturn(java.util.Optional.empty())

        val result = service.updateOrderStatus(
            order.orderId!!,
            OrderStatus.DELIVERED,
            captainId,
            OrderActor.CAPTAIN
        )

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
    fun `DELIVERED transition does not duplicate invoice if exists`() {
        val order = savedOrder(OrderStatus.PICKED_UP, PaymentStatus.SUCCESS, "CARD")
        order.captainId = captainId
        val invoice = Invoice(
            orderId = order.orderId!!,
            invoiceNumber = "INV-2026-ABCD",
            subtotalAmount = BigDecimal("500.00"),
            taxAmount = BigDecimal("90.00"),
            totalAmount = BigDecimal("590.00")
        )
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.saveAndFlush(any())).thenAnswer { it.getArgument<Order>(0) }
        whenever(invoiceRepository.findByOrderId(order.orderId!!)).thenReturn(java.util.Optional.of(invoice))

        val result = service.updateOrderStatus(
            order.orderId!!,
            OrderStatus.DELIVERED,
            captainId,
            OrderActor.CAPTAIN
        )

        assertEquals(OrderStatus.DELIVERED, result.status)
        verify(invoiceRepository, never()).save(any())
    }

    @Test
    fun `customer PLACED cancellation restores reserved stock`() {
        val order = savedOrder(OrderStatus.PLACED)
        val items = listOf(
            OrderItem(
                orderId = order.orderId!!,
                offeringId = UUID.randomUUID(),
                offeringNameSnapshot = "Item 1",
                unitPriceSnapshot = BigDecimal("100.00"),
                quantity = 2,
                lineTotal = BigDecimal("200.00")
            )
        )
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.saveAndFlush(any())).thenAnswer { it.getArgument<Order>(0) }
        whenever(orderItemRepository.findByOrderId(order.orderId!!)).thenReturn(items)
        whenever(
            restTemplate.exchange(
                any<String>(),
                eq(org.springframework.http.HttpMethod.PUT),
                anyOrNull(),
                eq(Map::class.java)
            )
        ).thenReturn(org.springframework.http.ResponseEntity.ok(emptyMap<String, Any>()))

        val result = service.updateOrderStatus(
            order.orderId!!,
            OrderStatus.CANCELLED,
            customerId,
            OrderActor.CUSTOMER,
            "customer request"
        )

        assertEquals(OrderStatus.CANCELLED, result.status)
        assertNotNull(result.cancelledAt)
        assertEquals("customer request", result.cancellationReason)
        verify(restTemplate).exchange(
            eq("http://localhost:8082/api/v1/internal/catalog/offerings/${items[0].offeringId}/restore-stock?quantity=2"),
            eq(org.springframework.http.HttpMethod.PUT),
            anyOrNull(),
            eq(Map::class.java)
        )
    }

    @Test
    fun `terminal order cannot transition`() {
        val order = savedOrder(OrderStatus.CANCELLED)
        whenever(orderRepository.findById(order.orderId!!)).thenReturn(java.util.Optional.of(order))

        assertThrows<OrderTransitionConflictException> {
            service.updateOrderStatus(
                order.orderId!!,
                OrderStatus.ACCEPTED,
                merchantId,
                OrderActor.MERCHANT
            )
        }

        verify(orderRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `createSupportCase creates support case and publishes event`() {
        val actorId = UUID.randomUUID()
        whenever(supportCaseRepository.save(any())).thenAnswer { invocation ->
            val supportCase = invocation.getArgument<SupportCase>(0)
            supportCase.also { it.supportCaseId = it.supportCaseId ?: UUID.randomUUID() }
        }

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
    fun `createSupportCase rejects invalid action type`() {
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
    fun `resolveSupportCase marks case resolved and publishes event`() {
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
        whenever(supportCaseRepository.save(any())).thenAnswer { it.getArgument<SupportCase>(0) }

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

    @Test
    fun `confirmOrder payment success keeps lifecycle PLACED and makes order merchant actionable`() {
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("500.00"),
            totalAmount = BigDecimal("500.00"),
            paymentMethod = "CARD",
            paymentStatus = PaymentStatus.PENDING
        )
        whenever(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.saveAndFlush(any())).thenAnswer { it.getArgument<Order>(0) }

        val paymentResponse = mapOf(
            "transactionId" to paymentId.toString(),
            "userId" to customerId.toString(),
            "referenceId" to orderId.toString(),
            "transactionType" to "ORDER_PAYMENT",
            "status" to "SUCCESS",
            "amount" to 500.0
        )
        whenever(
            restTemplate.exchange(
                eq("http://localhost:8090/api/v1/payments/transactions/$paymentId"),
                eq(org.springframework.http.HttpMethod.GET),
                any<org.springframework.http.HttpEntity<Any>>(),
                eq(Map::class.java)
            )
        ).thenReturn(org.springframework.http.ResponseEntity.ok(paymentResponse))

        val saved = service.confirmOrder(orderId, paymentId)

        assertEquals(OrderStatus.PLACED, saved.status)
        assertNull(saved.acceptedAt)
        assertEquals(paymentId, saved.paymentId)
        assertEquals(PaymentStatus.SUCCESS, saved.paymentStatus)
        verify(orderStatusHistoryRepository, never()).save(any())
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("ORDER"),
            aggregateId = eq(orderId),
            eventType = eq("MerchantOrderActionable"),
            eventPayload = any()
        )
    }

    @Test
    fun `confirmOrder payment status not SUCCESS throws and keeps PLACED`() {
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("500.00"),
            totalAmount = BigDecimal("500.00"),
            paymentMethod = "CARD",
            paymentStatus = PaymentStatus.PENDING
        )
        whenever(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order))

        val paymentResponse = mapOf(
            "transactionId" to paymentId.toString(),
            "userId" to customerId.toString(),
            "referenceId" to orderId.toString(),
            "transactionType" to "ORDER_PAYMENT",
            "status" to "FAILED",
            "amount" to 500.0
        )
        whenever(
            restTemplate.exchange(
                eq("http://localhost:8090/api/v1/payments/transactions/$paymentId"),
                eq(org.springframework.http.HttpMethod.GET),
                any<org.springframework.http.HttpEntity<Any>>(),
                eq(Map::class.java)
            )
        ).thenReturn(org.springframework.http.ResponseEntity.ok(paymentResponse))

        val ex = assertThrows<IllegalStateException> { service.confirmOrder(orderId, paymentId) }
        assertTrue(ex.message!!.contains("expected SUCCESS"))
        assertEquals(OrderStatus.PLACED, order.status)
        assertEquals(PaymentStatus.PENDING, order.paymentStatus)
        verify(orderRepository, never()).saveAndFlush(any())
    }
}
