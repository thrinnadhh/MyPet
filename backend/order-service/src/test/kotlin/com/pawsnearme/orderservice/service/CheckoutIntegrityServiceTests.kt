package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogOfferingSnapshot
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.LoyaltyRewardTerms
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PromotionTerms
import com.pawsnearme.common.module.ProviderLocationSnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.ServiceabilityDecision
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class CheckoutIntegrityServiceTests {
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val historyRepository: OrderStatusHistoryRepository = mock()
    private val catalogModule: CatalogModuleApi = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val discoveryModule: DiscoveryModuleApi = mock()
    private val quoteStore: QuoteStore = mock()
    private val outboxService: OutboxService = mock()
    private val compensationService: OrderCompensationService = mock()

    private val service = CheckoutIntegrityService(
        orderRepository,
        orderItemRepository,
        historyRepository,
        catalogModule,
        paymentModule,
        providerModule,
        discoveryModule,
        quoteStore,
        outboxService,
        compensationService,
        true,
    )

    @Test
    fun `pricing hierarchy is list price minus item discount coupon loyalty plus delivery and tax`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val rewardId = UUID.randomUUID()
        whenever(discoveryModule.checkServiceability(eq("Tirupati"), eq(13.63), eq(79.42), any()))
            .thenReturn(ServiceabilityDecision(true, null))
        whenever(providerModule.location(providerId)).thenReturn(
            ProviderLocationSnapshot(providerId, "Tirupati", "517501", 13.63, 79.42)
        )
        whenever(catalogModule.offering(offeringId)).thenReturn(
            CatalogOfferingSnapshot(
                offeringId = offeringId,
                providerId = providerId,
                name = "Dog Food",
                price = BigDecimal("100.00"),
                status = "ACTIVE",
                stockQuantity = 10,
                listPrice = BigDecimal("120.00"),
            )
        )
        whenever(paymentModule.promotionTerms("SAVE10", BigDecimal("200.00"), providerId, null))
            .thenReturn(PromotionTerms("PERCENTAGE", BigDecimal("10.00"), BigDecimal("100.00")))
        whenever(paymentModule.loyaltyRewardTerms(rewardId, customerId, providerId))
            .thenReturn(LoyaltyRewardTerms(rewardId, "STAR10", BigDecimal("30.00"), true))

        val quote = service.calculateQuote(
            CheckoutQuoteRequest(
                customerId = customerId,
                providerId = providerId,
                deliveryAddressId = addressId,
                items = listOf(OrderItemRequest(offeringId, 2)),
                couponCode = "SAVE10",
                loyaltyRewardId = rewardId,
                paymentMethod = "CARD",
                city = "Tirupati",
                latitude = 13.63,
                longitude = 79.42,
            )
        )

        assertEquals(0, quote.subtotal.compareTo(BigDecimal("240.00")))
        assertEquals(0, quote.itemDiscount.compareTo(BigDecimal("40.00")))
        assertEquals(0, quote.couponDiscount.compareTo(BigDecimal("20.00")))
        assertEquals(0, quote.loyaltyDiscount.compareTo(BigDecimal("30.00")))
        assertEquals(0, quote.deliveryFee.compareTo(BigDecimal("29.00")))
        assertEquals(0, quote.tax.compareTo(BigDecimal("7.50")))
        assertEquals(0, quote.payableTotal.compareTo(BigDecimal("186.50")))
    }

    @Test
    fun `ordinary loyalty reward cannot stack with a normal coupon`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val rewardId = UUID.randomUUID()
        whenever(discoveryModule.checkServiceability(any(), any(), any(), any()))
            .thenReturn(ServiceabilityDecision(true, null))
        whenever(providerModule.location(providerId)).thenReturn(
            ProviderLocationSnapshot(providerId, "Tirupati", "517501", 13.63, 79.42)
        )
        whenever(catalogModule.offering(offeringId)).thenReturn(
            CatalogOfferingSnapshot(offeringId, providerId, "Dog Food", BigDecimal("200.00"), "ACTIVE", 5)
        )
        whenever(paymentModule.promotionTerms("SAVE10", BigDecimal("200.00"), providerId, null))
            .thenReturn(PromotionTerms("PERCENTAGE", BigDecimal("10.00"), null))
        whenever(paymentModule.loyaltyRewardTerms(rewardId, customerId, providerId))
            .thenReturn(LoyaltyRewardTerms(rewardId, "STAR10", BigDecimal("50.00"), false))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.calculateQuote(
                CheckoutQuoteRequest(
                    customerId = customerId,
                    providerId = providerId,
                    deliveryAddressId = UUID.randomUUID(),
                    items = listOf(OrderItemRequest(offeringId, 1)),
                    couponCode = "SAVE10",
                    loyaltyRewardId = rewardId,
                    paymentMethod = "CARD",
                    city = "Tirupati",
                    latitude = 13.63,
                    longitude = 79.42,
                )
            )
        }
        assertEquals("This loyalty reward cannot be combined with a normal coupon", error.message)
    }

    @Test
    fun `merchant origin delivery fee grows with distance and rejects outside service radius`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        whenever(discoveryModule.checkServiceability(any(), any(), any(), any()))
            .thenReturn(ServiceabilityDecision(true, null))
        whenever(providerModule.location(providerId)).thenReturn(
            ProviderLocationSnapshot(providerId, "Tirupati", "517501", 13.63, 79.42)
        )
        whenever(catalogModule.offering(offeringId)).thenReturn(
            CatalogOfferingSnapshot(offeringId, providerId, "Dog Food", BigDecimal("200.00"), "ACTIVE", 5)
        )

        val near = service.calculateQuote(
            CheckoutQuoteRequest(customerId, providerId, UUID.randomUUID(), listOf(OrderItemRequest(offeringId, 1)), paymentMethod = "CARD", city = "Tirupati", latitude = 13.63, longitude = 79.42)
        )
        val farther = service.calculateQuote(
            CheckoutQuoteRequest(customerId, providerId, UUID.randomUUID(), listOf(OrderItemRequest(offeringId, 1)), paymentMethod = "CARD", city = "Tirupati", latitude = 13.68, longitude = 79.42)
        )
        assertFalse(farther.deliveryFee <= near.deliveryFee)

        assertThrows(IllegalArgumentException::class.java) {
            service.calculateQuote(
                CheckoutQuoteRequest(customerId, providerId, UUID.randomUUID(), listOf(OrderItemRequest(offeringId, 1)), paymentMethod = "CARD", city = "Tirupati", latitude = 14.2, longitude = 79.42)
            )
        }
    }

    @Test
    fun `duplicate checkout request returns existing order before quote consumption or stock reserve`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val quoteToken = "Q-duplicate"
        val expectedRequestId = UUID.nameUUIDFromBytes("ORDER:$customerId:checkout:$quoteToken".toByteArray())
        val existing = Order(
            orderId = UUID.randomUUID(),
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = UUID.randomUUID(),
            checkoutRequestId = expectedRequestId,
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("200.00"),
            totalAmount = BigDecimal("229.00"),
            paymentMethod = "COD",
            paymentStatus = PaymentStatus.COD_PENDING,
        )
        whenever(orderRepository.findByCheckoutRequestId(expectedRequestId)).thenReturn(existing)

        val returned = service.createOrder(
            CreateOrderRequest(
                customerId = customerId,
                providerId = providerId,
                deliveryAddressId = existing.deliveryAddressId,
                items = listOf(OrderItemRequest(UUID.randomUUID(), 1)),
                paymentMethod = "COD",
                quoteToken = quoteToken,
            ),
            "checkout:$quoteToken",
        )

        assertEquals(existing.orderId, returned.orderId)
        verify(quoteStore, never()).consume(any())
        verify(catalogModule, never()).reserveStock(any())
        verify(paymentModule, never()).reserveCoupon(any())
    }
}
