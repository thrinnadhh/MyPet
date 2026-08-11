package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.OrderStatusHistory
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class MerchantOrderOperationalServiceTests {
    private val orderRepository: OrderRepository = mock()
    private val itemRepository: OrderItemRepository = mock()
    private val historyRepository: OrderStatusHistoryRepository = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val entityManager: EntityManager = mock()
    private val query: Query = mock()
    private val service = MerchantOrderOperationalService(
        orderRepository,
        itemRepository,
        historyRepository,
        providerModule,
        entityManager,
    )

    @Test
    fun `merchant detail returns customer address items pricing SLA and actor history`() {
        val merchantId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val placedAt = Instant.parse("2026-08-10T10:00:00Z")
        val acceptedAt = Instant.parse("2026-08-10T10:02:00Z")
        val preparingAt = Instant.parse("2026-08-10T10:05:00Z")
        val merchantActor = merchantId
        val order = Order(
            orderId = orderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = addressId,
            deliveryContactPhone = "+919876543210",
            deliveryContactVerified = true,
            status = OrderStatus.PREPARING,
            subtotalAmount = BigDecimal("500.00"),
            deliveryFee = BigDecimal("29.00"),
            discountAmount = BigDecimal("50.00"),
            taxAmount = BigDecimal("22.50"),
            totalAmount = BigDecimal("501.50"),
            paymentMethod = "UPI",
            paymentStatus = PaymentStatus.SUCCESS,
            placedAt = placedAt,
            acceptedAt = acceptedAt,
            preparingAt = null,
        )
        val item = OrderItem(
            orderItemId = itemId,
            orderId = orderId,
            offeringId = UUID.randomUUID(),
            offeringNameSnapshot = "Dog Food 1kg",
            unitPriceSnapshot = BigDecimal("250.00"),
            quantity = 2,
            lineTotal = BigDecimal("500.00"),
        )
        val history = listOf(
            OrderStatusHistory(
                orderId = orderId,
                fromStatus = OrderStatus.PLACED,
                toStatus = OrderStatus.ACCEPTED,
                changedAt = acceptedAt,
                changedByUserId = merchantActor,
                note = "Accepted by store",
            ),
            OrderStatusHistory(
                orderId = orderId,
                fromStatus = OrderStatus.ACCEPTED,
                toStatus = OrderStatus.PREPARING,
                changedAt = preparingAt,
                changedByUserId = merchantActor,
                note = "Packing started",
            ),
        )
        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
        whenever(providerModule.ownerUserId(providerId)).thenReturn(merchantId)
        whenever(itemRepository.findByOrderId(orderId)).thenReturn(listOf(item))
        whenever(historyRepository.findByOrderId(orderId)).thenReturn(history)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("addressId"), eq(addressId))).thenReturn(query)
        whenever(query.setParameter(eq("customerId"), eq(customerId))).thenReturn(query)
        whenever(query.resultList).thenReturn(
            listOf(
                arrayOf<Any?>(
                    "Trinadh",
                    "Home",
                    "10-1 Main Road",
                    "Near Park",
                    "Tirupati",
                    "Andhra Pradesh",
                    "517501",
                    BigDecimal("13.630000"),
                    BigDecimal("79.420000"),
                )
            )
        )

        val detail = service.detail(orderId, merchantId)

        assertEquals("Trinadh", detail.customerName)
        assertEquals("10-1 Main Road", detail.deliveryAddress.line1)
        assertEquals("+919876543210", detail.contactPhone)
        assertEquals(1, detail.items.size)
        assertEquals(2, detail.items.single().quantity)
        assertEquals(BigDecimal("50.00"), detail.discount)
        assertEquals(PaymentStatus.SUCCESS, detail.paymentStatus)
        assertEquals(acceptedAt, detail.acceptedAt)
        assertEquals(preparingAt, detail.preparingAt)
        assertEquals(merchantActor, detail.history.last().actorId)
        assertEquals("Packing started", detail.history.last().note)
    }

    @Test
    fun `merchant cannot read another providers operational order`() {
        val orderId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val callerId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = UUID.randomUUID(),
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            subtotalAmount = BigDecimal("200.00"),
            totalAmount = BigDecimal("229.00"),
        )
        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
        whenever(providerModule.ownerUserId(providerId)).thenReturn(ownerId)

        val error = assertThrows(OrderAccessDeniedException::class.java) {
            service.detail(orderId, callerId)
        }

        assertEquals("Access denied to merchant operational order detail.", error.message)
    }
}
