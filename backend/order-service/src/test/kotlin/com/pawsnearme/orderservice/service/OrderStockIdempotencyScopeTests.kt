package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogOfferingSnapshot
import com.pawsnearme.common.module.CodEligibilityDecision
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.repository.DisputeRepository
import com.pawsnearme.orderservice.repository.InvoiceRepository
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import com.pawsnearme.orderservice.repository.SupportCaseRepository
import com.pawsnearme.orderservice.repository.SystemConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OrderStockIdempotencyScopeTests {
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val historyRepository: OrderStatusHistoryRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val configRepository: SystemConfigRepository = mock()
    private val disputeRepository: DisputeRepository = mock()
    private val invoiceRepository: InvoiceRepository = mock()
    private val supportCaseRepository: SupportCaseRepository = mock()
    private val outboxService: OutboxService = mock()
    private val catalogModule: CatalogModuleApi = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val discoveryModule: DiscoveryModuleApi = mock()
    private val quoteStore: QuoteStore = mock()
    private val compensationService: OrderCompensationService = mock()

    @Test
    fun `different orders with same product and quantity use different reserve and restore keys`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val item = OrderItemRequest(offeringId, 1)
        val total = BigDecimal("154.00")
        val offering = CatalogOfferingSnapshot(
            offeringId = offeringId,
            providerId = providerId,
            name = "Dog Food",
            price = BigDecimal("100.00"),
            status = "ACTIVE",
            stockQuantity = 100,
        )

        whenever(catalogModule.offering(offeringId)).thenReturn(offering)
        whenever(catalogModule.reserveStock(any())).thenReturn(offering)
        whenever(catalogModule.restoreStock(any())).thenReturn(offering)
        whenever(paymentModule.codEligibility(any(), anyOrNull(), anyOrNull())).thenReturn(
            CodEligibilityDecision(true, BigDecimal("5000.00"), null)
        )
        whenever(quoteStore.store(any(), any())).thenAnswer { it.getArgument(0) }
        whenever(quoteStore.consume("Q-ORDER-A")).thenReturn(
            QuoteSnapshot(total, null, customerId, providerId, "COD", addressId, null, listOf(QuoteItemSnapshot(offeringId, 1)))
        )
        whenever(quoteStore.consume("Q-ORDER-B")).thenReturn(
            QuoteSnapshot(total, null, customerId, providerId, "COD", addressId, null, listOf(QuoteItemSnapshot(offeringId, 1)))
        )

        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        val index = AtomicInteger()
        whenever(orderRepository.save(any<Order>())).thenAnswer { invocation ->
            invocation.getArgument<Order>(0).also { order ->
                if (order.orderId == null) order.orderId = ids[index.getAndIncrement()]
            }
        }
        whenever(orderItemRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(historyRepository.save(any())).thenAnswer { it.getArgument(0) }

        val service = OrderService(
            orderRepository,
            orderItemRepository,
            historyRepository,
            kafkaTemplate,
            configRepository,
            disputeRepository,
            invoiceRepository,
            supportCaseRepository,
            outboxService,
            catalogModule,
            paymentModule,
            providerModule,
            discoveryModule,
            false,
            quoteStore,
            compensationService,
        )

        val first = service.createOrder(
            CreateOrderRequest(
                customerId = customerId,
                providerId = providerId,
                deliveryAddressId = addressId,
                items = listOf(item),
                paymentMethod = "COD",
                quoteToken = "Q-ORDER-A",
            )
        )
        val second = service.createOrder(
            CreateOrderRequest(
                customerId = customerId,
                providerId = providerId,
                deliveryAddressId = addressId,
                items = listOf(item),
                paymentMethod = "COD",
                quoteToken = "Q-ORDER-B",
            )
        )

        val reserveCaptor = argumentCaptor<StockMutationCommand>()
        verify(catalogModule, times(2)).reserveStock(reserveCaptor.capture())
        val reserveKeys = reserveCaptor.allValues.map { it.idempotencyKey }
        assertEquals(2, reserveKeys.toSet().size)
        assertNotEquals(reserveKeys[0], reserveKeys[1])

        val firstId = requireNotNull(first.orderId)
        val secondId = requireNotNull(second.orderId)
        whenever(orderRepository.findById(firstId)).thenReturn(Optional.of(first))
        whenever(orderRepository.findById(secondId)).thenReturn(Optional.of(second))
        whenever(orderItemRepository.findByOrderId(firstId)).thenReturn(
            listOf(
                OrderItem(
                    orderId = firstId,
                    offeringId = offeringId,
                    offeringNameSnapshot = "Dog Food",
                    unitPriceSnapshot = BigDecimal("100.00"),
                    quantity = 1,
                    lineTotal = BigDecimal("100.00"),
                )
            )
        )
        whenever(orderItemRepository.findByOrderId(secondId)).thenReturn(
            listOf(
                OrderItem(
                    orderId = secondId,
                    offeringId = offeringId,
                    offeringNameSnapshot = "Dog Food",
                    unitPriceSnapshot = BigDecimal("100.00"),
                    quantity = 1,
                    lineTotal = BigDecimal("100.00"),
                )
            )
        )

        service.cancelOrder(firstId, customerId, "CUSTOMER", "Changed mind")
        service.cancelOrder(secondId, customerId, "CUSTOMER", "Changed mind")

        val restoreCaptor = argumentCaptor<StockMutationCommand>()
        verify(catalogModule, times(2)).restoreStock(restoreCaptor.capture())
        val restoreKeys = restoreCaptor.allValues.map { it.idempotencyKey }
        assertEquals(2, restoreKeys.toSet().size)
        assertNotEquals(restoreKeys[0], restoreKeys[1])
    }
}
