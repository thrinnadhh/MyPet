package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class RecurringOccurrenceOrderCreatorTests {
    private val orderRepository: OrderRepository = mock()
    private val orderService: OrderService = mock()
    private val quoteStore: QuoteStore = mock()
    private val creator = RecurringOccurrenceOrderCreator(orderRepository, orderService, quoteStore)

    private val occurrenceId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val addressId = UUID.randomUUID()
    private val offeringId = UUID.randomUUID()

    @Test
    fun `committed occurrence order is reconciled without reserving stock again`() {
        val existing = order(recurringOccurrenceId = occurrenceId)
        whenever(orderRepository.findByRecurringOccurrenceId(occurrenceId)).thenReturn(Optional.of(existing))

        val result = create()

        assertEquals(existing.orderId, result.orderId)
        assertEquals(occurrenceId, result.recurringOccurrenceId)
        verifyNoInteractions(orderService, quoteStore)
        verify(orderRepository, never()).save(any())
    }

    @Test
    fun `new occurrence uses deterministic quote token and persists occurrence identity`() {
        whenever(orderRepository.findByRecurringOccurrenceId(occurrenceId)).thenReturn(Optional.empty())
        whenever(orderService.calculateQuote(any())).thenReturn(
            CheckoutQuoteResponse(
                quoteToken = "random-public-quote",
                subtotal = BigDecimal("200.00"),
                itemDiscount = BigDecimal.ZERO,
                couponDiscount = BigDecimal.ZERO,
                loyaltyDiscount = BigDecimal.ZERO,
                deliveryFee = BigDecimal("49.00"),
                tax = BigDecimal("10.00"),
                roundOff = BigDecimal.ZERO,
                payableTotal = BigDecimal("259.00"),
                paymentMethod = "COD",
                expiresAt = Instant.now().plusSeconds(120),
            )
        )
        whenever(quoteStore.store(any(), any())).thenAnswer { it.getArgument(0) }
        val created = order(recurringOccurrenceId = null)
        whenever(orderService.createOrder(any())).thenReturn(created)
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.getArgument(0) }

        val result = create()

        val expectedToken = "R-$occurrenceId"
        verify(quoteStore).delete("random-public-quote")
        val snapshotCaptor = argumentCaptor<QuoteSnapshot>()
        verify(quoteStore).store(eq(expectedToken), snapshotCaptor.capture())
        val requestCaptor = argumentCaptor<CreateOrderRequest>()
        verify(orderService).createOrder(requestCaptor.capture())

        assertEquals(expectedToken, requestCaptor.firstValue.quoteToken)
        assertEquals(customerId, snapshotCaptor.firstValue.customerId)
        assertEquals(providerId, snapshotCaptor.firstValue.providerId)
        assertEquals(addressId, snapshotCaptor.firstValue.deliveryAddressId)
        assertNull(snapshotCaptor.firstValue.couponCode)
        assertNull(snapshotCaptor.firstValue.loyaltyRewardId)
        assertEquals(occurrenceId, result.recurringOccurrenceId)
        verify(orderRepository).save(created)
    }

    private fun create(): Order = creator.createOrGet(
        occurrenceId = occurrenceId,
        customerId = customerId,
        providerId = providerId,
        deliveryAddressId = addressId,
        items = listOf(OrderItemRequest(offeringId, 2)),
        paymentMethod = "COD",
        city = "Tirupati",
        latitude = 13.6288,
        longitude = 79.4192,
    )

    private fun order(recurringOccurrenceId: UUID?): Order = Order(
        orderId = UUID.randomUUID(),
        customerId = customerId,
        providerId = providerId,
        deliveryAddressId = addressId,
        status = OrderStatus.PLACED,
        subtotalAmount = BigDecimal("200.00"),
        totalAmount = BigDecimal("259.00"),
        paymentMethod = "COD",
        paymentStatus = "COD_PENDING",
        recurringOccurrenceId = recurringOccurrenceId,
    )
}