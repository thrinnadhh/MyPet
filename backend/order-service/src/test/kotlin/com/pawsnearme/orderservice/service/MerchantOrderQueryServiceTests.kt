package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.util.UUID

class MerchantOrderQueryServiceTests {
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val service = MerchantOrderQueryService(orderRepository, orderItemRepository, providerModule)

    @Test
    fun `provider owner receives authoritative item snapshots and totals from bounded page`() {
        val ownerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = UUID.randomUUID(),
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            deliveryContactPhone = "+919876543210",
            deliveryContactVerified = true,
            subtotalAmount = BigDecimal("450.00"),
            deliveryFee = BigDecimal("49.00"),
            discountAmount = BigDecimal("50.00"),
            taxAmount = BigDecimal("20.00"),
            totalAmount = BigDecimal("469.00"),
            couponCode = "SAVE50",
            paymentMethod = "COD",
            paymentStatus = "COD_PENDING",
        )
        val item = OrderItem(
            orderId = orderId,
            offeringId = offeringId,
            offeringNameSnapshot = "Dog Food 2kg",
            unitPriceSnapshot = BigDecimal("225.00"),
            quantity = 2,
            lineTotal = BigDecimal("450.00"),
        )
        whenever(providerModule.ownerUserId(providerId)).thenReturn(ownerId)
        whenever(orderRepository.findByProviderIdOrderByPlacedAtDesc(eq(providerId), any<Pageable>())).thenAnswer { invocation ->
            val pageable = invocation.arguments[1] as Pageable
            assertEquals(100, pageable.pageSize)
            PageImpl(listOf(order), pageable, 1)
        }
        whenever(orderItemRepository.findByOrderIdIn(listOf(orderId))).thenReturn(listOf(item))

        val view = service.listProviderOrders(providerId, ownerId, "MERCHANT").single()

        assertEquals(orderId, view.orderId)
        assertEquals("SAVE50", view.couponCode)
        assertEquals(BigDecimal("50.00"), view.discountAmount)
        assertEquals("Dog Food 2kg", view.items.single().name)
        assertEquals(2, view.items.single().quantity)
        assertEquals(BigDecimal("225.00"), view.items.single().unitPrice)
        assertEquals("+919876543210", view.deliveryContactPhone)
        assertEquals(true, view.deliveryContactVerified)
        verify(orderItemRepository).findByOrderIdIn(listOf(orderId))
    }

    @Test
    fun `merchant order page size is capped at one hundred`() {
        val ownerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        whenever(providerModule.ownerUserId(providerId)).thenReturn(ownerId)
        whenever(orderRepository.findByProviderIdOrderByPlacedAtDesc(eq(providerId), any<Pageable>())).thenAnswer { invocation ->
            val pageable = invocation.arguments[1] as Pageable
            assertEquals(100, pageable.pageSize)
            assertEquals(0, pageable.pageNumber)
            PageImpl<Order>(emptyList(), pageable, 0)
        }

        val page = service.listProviderOrdersPage(providerId, ownerId, "MERCHANT", page = -5, size = 5000)

        assertEquals(0, page.page)
        assertEquals(100, page.size)
        assertEquals(0, page.totalElements)
    }

    @Test
    fun `merchant cannot inspect another providers orders`() {
        val providerId = UUID.randomUUID()
        whenever(providerModule.ownerUserId(providerId)).thenReturn(UUID.randomUUID())

        assertThrows<OrderAccessDeniedException> {
            service.listProviderOrders(providerId, UUID.randomUUID(), "MERCHANT")
        }
    }

    @Test
    fun `customer cannot use merchant provider order projection`() {
        assertThrows<OrderAccessDeniedException> {
            service.listProviderOrders(UUID.randomUUID(), UUID.randomUUID(), "CUSTOMER")
        }
    }
}
