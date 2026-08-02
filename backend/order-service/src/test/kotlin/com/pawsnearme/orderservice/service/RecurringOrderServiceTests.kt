package com.pawsnearme.orderservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private lateinit var orderRepository: OrderRepository
    private lateinit var orderService: OrderService
    private lateinit var outboxService: OutboxService
    private lateinit var service: RecurringOrderService

    private val customerId = UUID.randomUUID()
    private val sourceOrderId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        repository = mock()
        orderRepository = mock()
        orderService = mock()
        outboxService = mock()
        service = RecurringOrderService(repository, orderRepository, orderService, outboxService)
    }

    @Test
    fun `creates only allowed confirmation based cadence from completed customer order`() {
        val order = completedOrder()
        whenever(orderRepository.findById(sourceOrderId)).thenReturn(Optional.of(order))
        whenever(repository.existsByCustomerIdAndSourceOrderIdAndStatusNot(customerId, sourceOrderId, RecurringOrderStatus.CANCELLED)).thenReturn(false)
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }

        val result = service.create(customerId, CreateRecurringOrderRequest(sourceOrderId, 15))

        assertEquals(15, result.cadenceDays)
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
    fun `due schedule becomes awaiting confirmation and never creates an order`() {
        val due = subscription(status = RecurringOrderStatus.ACTIVE, nextOrderAt = Instant.now().minusSeconds(5))
        whenever(repository.findByStatusAndNextOrderAtLessThanEqual(eq(RecurringOrderStatus.ACTIVE), any())).thenReturn(listOf(due))
        whenever(repository.save(any<RecurringOrderSubscription>())).thenAnswer { it.getArgument(0) }

        assertEquals(1, service.markDueForConfirmation())
        assertEquals(RecurringOrderStatus.AWAITING_CONFIRMATION, due.status)
        verify(orderService, never()).createOrder(any())
        verify(outboxService).saveEvent(any(), eq("RECURRING_ORDER"), any(), eq("RecurringOrderConfirmationRequired"), any())
    }

    @Test
    fun `confirmation only revalidates and advances schedule when available`() {
        val due = subscription(status = RecurringOrderStatus.AWAITING_CONFIRMATION, nextOrderAt = Instant.now())
        whenever(repository.findById(due.subscriptionId)).thenReturn(Optional.of(due))
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
        verify(orderService, never()).createOrder(any())
    }

    @Test
    fun `unavailable confirmation remains awaiting customer changes`() {
        val due = subscription(status = RecurringOrderStatus.AWAITING_CONFIRMATION, nextOrderAt = Instant.now())
        whenever(repository.findById(due.subscriptionId)).thenReturn(Optional.of(due))
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
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = OrderStatus.COMPLETED,
        subtotalAmount = BigDecimal("500"),
        totalAmount = BigDecimal("500")
    )

    private fun subscription(status: RecurringOrderStatus, nextOrderAt: Instant) = RecurringOrderSubscription(
        customerId = customerId,
        providerId = UUID.randomUUID(),
        sourceOrderId = sourceOrderId,
        deliveryAddressId = UUID.randomUUID(),
        cadenceDays = 30,
        status = status,
        nextOrderAt = nextOrderAt
    )
}
