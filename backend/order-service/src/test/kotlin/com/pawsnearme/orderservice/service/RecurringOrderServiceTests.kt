package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.DeliveryAddressSnapshot
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.ServiceabilityDecision
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderOccurrence
import com.pawsnearme.orderservice.model.RecurringOrderOccurrenceStatus
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.RecurringOrderOccurrenceRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class RecurringOrderServiceTests {
    private lateinit var repository: RecurringOrderSubscriptionRepository
    private lateinit var occurrenceRepository: RecurringOrderOccurrenceRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var orderItemRepository: OrderItemRepository
    private lateinit var orderService: OrderService
    private lateinit var providerModule: ProviderModuleApi
    private lateinit var discoveryModule: DiscoveryModuleApi
    private lateinit var outboxService: OutboxService
    private lateinit var service: RecurringOrderService

    private val customerId = UUID.randomUUID()
    private val sourceOrderId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val addressId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        repository = mock()
        occurrenceRepository = mock()
        orderRepository = mock()
        orderItemRepository = mock()
        orderService = mock()
        providerModule = mock()
        discoveryModule = mock()
        outboxService = mock()
        service = RecurringOrderService(
            repository,
            occurrenceRepository,
            orderRepository,
            orderItemRepository,
            orderService,
            providerModule,
            discoveryModule,
            outboxService,
        )
        whenever(providerModule.providerOperational(providerId)).thenReturn(true)
        whenever(providerModule.deliveryAddress(customerId, addressId)).thenReturn(address())
        whenever(discoveryModule.checkServiceability(any(), any(), any(), any())).thenReturn(ServiceabilityDecision(true, null))
    }

    @Test
    fun `creates allowed recurring cadence from completed customer order`() {
        val order = completedOrder()
        whenever(orderRepository.findById(sourceOrderId)).thenReturn(Optional.of(order))
        whenever(repository.existsByCustomerIdAndSourceOrderIdAndStatusNot(customerId, sourceOrderId, RecurringOrderStatus.CANCELLED)).thenReturn(false)
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }

        val result = service.create(customerId, CreateRecurringOrderRequest(sourceOrderId, 15))

        assertEquals(15, result.cadenceDays)
        assertEquals("COD", result.paymentMethod)
        assertEquals(RecurringOrderStatus.ACTIVE, result.status)
        assertTrue(result.nextOrderAt.isAfter(Instant.now().plus(14, ChronoUnit.DAYS)))
        verify(outboxService).saveEvent(any(), eq("RECURRING_ORDER"), any(), eq("RecurringOrderCreated"), any())
    }

    @Test
    fun `rejects unsupported cadence`() {
        assertThrows<IllegalArgumentException> {
            service.create(customerId, CreateRecurringOrderRequest(sourceOrderId, 10))
        }
        verify(orderRepository, never()).findById(any())
    }

    @Test
    fun `due schedule creates one real operational order and advances occurrence`() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val due = subscription(status = RecurringOrderStatus.ACTIVE, nextOrderAt = now.minusSeconds(5))
        val generatedOrderId = UUID.randomUUID()
        val generated = Order(
            orderId = generatedOrderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = addressId,
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("200.00"),
            totalAmount = BigDecimal("254.00"),
            paymentMethod = "COD",
            paymentStatus = "COD_PENDING",
        )
        whenever(repository.findByStatusAndNextOrderAtLessThanEqual(RecurringOrderStatus.ACTIVE, now)).thenReturn(listOf(due))
        whenever(repository.findByIdForUpdate(due.subscriptionId)).thenReturn(Optional.of(due))
        whenever(occurrenceRepository.findBySubscriptionIdAndScheduledFor(due.subscriptionId, due.nextOrderAt)).thenReturn(null)
        whenever(occurrenceRepository.save(any<RecurringOrderOccurrence>())).thenAnswer { it.getArgument(0) }
        whenever(orderRepository.findById(sourceOrderId)).thenReturn(Optional.of(completedOrder()))
        whenever(orderItemRepository.findByOrderId(sourceOrderId)).thenReturn(sourceItems())
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }
        whenever(orderService.calculateQuote(any())).thenReturn(
            CheckoutQuoteResponse(
                quoteToken = "recurring-quote",
                subtotal = BigDecimal("200.00"),
                itemDiscount = BigDecimal.ZERO,
                couponDiscount = BigDecimal.ZERO,
                loyaltyDiscount = BigDecimal.ZERO,
                deliveryFee = BigDecimal("40.00"),
                tax = BigDecimal("14.00"),
                roundOff = BigDecimal.ZERO,
                payableTotal = BigDecimal("254.00"),
                paymentMethod = "COD",
                expiresAt = now.plusSeconds(120),
            )
        )
        whenever(orderService.createOrder(any())).thenReturn(generated)

        val previousSchedule = due.nextOrderAt
        val result = service.processDueOrders(now)

        assertEquals(1, result.ordersCreated)
        assertEquals(0, result.failed)
        assertEquals(generatedOrderId, due.lastOrderId)
        assertEquals(previousSchedule.plus(30, ChronoUnit.DAYS), due.nextOrderAt)
        assertNotNull(due.lastExecutedAt)
        verify(orderService).createOrder(any())
        verify(outboxService).saveEvent(any(), eq("RECURRING_ORDER"), any(), eq("RecurringOrderGenerated"), any())
    }

    @Test
    fun `existing occurrence prevents duplicate worker order generation`() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val due = subscription(status = RecurringOrderStatus.ACTIVE, nextOrderAt = now.minusSeconds(5))
        val scheduledFor = due.nextOrderAt
        val existing = RecurringOrderOccurrence(
            subscriptionId = due.subscriptionId,
            scheduledFor = scheduledFor,
            orderId = UUID.randomUUID(),
            status = RecurringOrderOccurrenceStatus.ORDER_CREATED,
        )
        whenever(repository.findByStatusAndNextOrderAtLessThanEqual(RecurringOrderStatus.ACTIVE, now)).thenReturn(listOf(due))
        whenever(repository.findByIdForUpdate(due.subscriptionId)).thenReturn(Optional.of(due))
        whenever(occurrenceRepository.findBySubscriptionIdAndScheduledFor(due.subscriptionId, scheduledFor)).thenReturn(existing)
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }

        val result = service.processDueOrders(now)

        assertEquals(1, result.skipped)
        assertEquals(existing.orderId, due.lastOrderId)
        assertEquals(scheduledFor.plus(30, ChronoUnit.DAYS), due.nextOrderAt)
        verify(orderService, never()).createOrder(any())
    }

    @Test
    fun `out of stock creates failed occurrence without invalid order`() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val due = subscription(status = RecurringOrderStatus.ACTIVE, nextOrderAt = now.minusSeconds(5))
        whenever(repository.findByStatusAndNextOrderAtLessThanEqual(RecurringOrderStatus.ACTIVE, now)).thenReturn(listOf(due))
        whenever(repository.findByIdForUpdate(due.subscriptionId)).thenReturn(Optional.of(due))
        whenever(occurrenceRepository.findBySubscriptionIdAndScheduledFor(due.subscriptionId, due.nextOrderAt)).thenReturn(null)
        whenever(occurrenceRepository.save(any<RecurringOrderOccurrence>())).thenAnswer { it.getArgument(0) }
        whenever(orderRepository.findById(sourceOrderId)).thenReturn(Optional.of(completedOrder()))
        whenever(orderItemRepository.findByOrderId(sourceOrderId)).thenReturn(sourceItems())
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }
        whenever(orderService.calculateQuote(any())).thenThrow(IllegalStateException("Insufficient stock for offering"))

        val result = service.processDueOrders(now)

        assertEquals(1, result.failed)
        assertEquals("OUT_OF_STOCK", due.lastFailureCode)
        verify(orderService, never()).createOrder(any())
        verify(outboxService).saveEvent(any(), eq("RECURRING_ORDER"), any(), eq("RecurringOrderFailed"), any())
    }

    @Test
    fun `merchant cannot inspect another providers subscriptions`() {
        whenever(providerModule.ownerUserId(providerId)).thenReturn(UUID.randomUUID())
        assertThrows<OrderAccessDeniedException> {
            service.listForProvider(providerId, UUID.randomUUID(), "MERCHANT")
        }
        verify(repository, never()).findByProviderIdOrderByNextOrderAtAsc(any())
    }

    @Test
    fun `legacy confirmation only reactivates row for automatic execution`() {
        val due = subscription(status = RecurringOrderStatus.AWAITING_CONFIRMATION, nextOrderAt = Instant.now())
        whenever(repository.findByIdForUpdate(due.subscriptionId)).thenReturn(Optional.of(due))
        whenever(orderService.revalidateReorder(due.sourceOrderId, customerId, "CUSTOMER")).thenReturn(
            ReorderValidationResponse(
                originalOrderId = due.sourceOrderId,
                providerId = due.providerId,
                isProviderServiceable = true,
                items = emptyList(),
                canReorder = true
            )
        )
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }

        val result = service.confirm(customerId, due.subscriptionId)

        assertTrue(result.reorder.canReorder)
        assertEquals(RecurringOrderStatus.ACTIVE, result.subscription.status)
        assertTrue(!result.subscription.nextOrderAt.isAfter(Instant.now().plusSeconds(2)))
        verify(orderService, never()).createOrder(any())
    }

    @Test
    fun `unavailable legacy confirmation remains awaiting customer changes`() {
        val due = subscription(status = RecurringOrderStatus.AWAITING_CONFIRMATION, nextOrderAt = Instant.now())
        whenever(repository.findByIdForUpdate(due.subscriptionId)).thenReturn(Optional.of(due))
        whenever(orderService.revalidateReorder(due.sourceOrderId, customerId, "CUSTOMER")).thenReturn(
            ReorderValidationResponse(due.sourceOrderId, due.providerId, false, emptyList(), false)
        )

        val result = service.confirm(customerId, due.subscriptionId)

        assertFalse(result.reorder.canReorder)
        assertEquals(RecurringOrderStatus.AWAITING_CONFIRMATION, result.subscription.status)
        verify(repository, never()).save(due)
    }

    private fun completedOrder() = Order(
        orderId = sourceOrderId,
        customerId = customerId,
        providerId = providerId,
        deliveryAddressId = addressId,
        status = OrderStatus.COMPLETED,
        subtotalAmount = BigDecimal("500"),
        totalAmount = BigDecimal("500"),
        paymentMethod = "COD",
        paymentStatus = "COD_COLLECTED",
    )

    private fun sourceItems() = listOf(
        OrderItem(
            orderId = sourceOrderId,
            offeringId = UUID.randomUUID(),
            offeringNameSnapshot = "Recurring food",
            unitPriceSnapshot = BigDecimal("100.00"),
            quantity = 2,
            lineTotal = BigDecimal("200.00"),
        )
    )

    private fun address() = DeliveryAddressSnapshot(
        addressId = addressId,
        customerId = customerId,
        city = "Tirupati",
        pincode = "517501",
        latitude = 13.6288,
        longitude = 79.4192,
    )

    private fun subscription(status: RecurringOrderStatus, nextOrderAt: Instant) = RecurringOrderSubscription(
        customerId = customerId,
        providerId = providerId,
        sourceOrderId = sourceOrderId,
        deliveryAddressId = addressId,
        cadenceDays = 30,
        paymentMethod = "COD",
        status = status,
        nextOrderAt = nextOrderAt
    )
}