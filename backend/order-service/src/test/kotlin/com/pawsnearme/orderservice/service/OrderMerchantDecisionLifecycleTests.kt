package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PaymentTransactionSnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.DisputeRepository
import com.pawsnearme.orderservice.repository.InvoiceRepository
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import com.pawsnearme.orderservice.repository.SupportCaseRepository
import com.pawsnearme.orderservice.repository.SystemConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class OrderMerchantDecisionLifecycleTests {
    private val orderRepository: OrderRepository = mock()
    private val itemRepository: OrderItemRepository = mock()
    private val historyRepository: OrderStatusHistoryRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val configRepository: SystemConfigRepository = mock()
    private val disputeRepository: DisputeRepository = mock()
    private val invoiceRepository: InvoiceRepository = mock()
    private val supportRepository: SupportCaseRepository = mock()
    private val outboxService: OutboxService = mock()
    private val catalogModule: CatalogModuleApi = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val discoveryModule: DiscoveryModuleApi = mock()

    private val service = OrderService(
        orderRepository,
        itemRepository,
        historyRepository,
        kafkaTemplate,
        configRepository,
        disputeRepository,
        invoiceRepository,
        supportRepository,
        outboxService,
        catalogModule,
        paymentModule,
        providerModule,
        discoveryModule,
    )

    @Test
    fun `COD order requires explicit merchant acceptance`() {
        val merchantId = UUID.randomUUID()
        val order = placedOrder(paymentMethod = "COD", paymentStatus = "COD_PENDING")
        whenever(orderRepository.findByIdForUpdate(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(providerModule.ownerUserId(order.providerId)).thenReturn(merchantId)
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.getArgument(0) }
        whenever(historyRepository.save(any())).thenAnswer { it.getArgument(0) }

        val accepted = service.updateOrderStatusWithAuth(
            order.orderId!!,
            OrderStatus.ACCEPTED,
            merchantId,
            "MERCHANT",
            "Accepted by store",
        )

        assertEquals(OrderStatus.ACCEPTED, accepted.status)
        assertNotNull(accepted.acceptedAt)
        assertEquals("COD_PENDING", accepted.paymentStatus)
        verify(orderRepository).findByIdForUpdate(order.orderId!!)
    }

    @Test
    fun `prepaid payment confirmation does not accept the order`() {
        val paymentId = UUID.randomUUID()
        val order = placedOrder(paymentMethod = "CARD", paymentStatus = "PENDING")
        whenever(orderRepository.findByIdForUpdate(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.getArgument(0) }
        whenever(paymentModule.transaction(paymentId)).thenReturn(
            PaymentTransactionSnapshot(
                transactionId = paymentId,
                userId = order.customerId,
                referenceId = order.orderId!!,
                transactionType = "ORDER_PAYMENT",
                amount = order.totalAmount,
                status = "SUCCESS",
            )
        )

        val paid = service.confirmOrder(order.orderId!!, paymentId)

        assertEquals(OrderStatus.PLACED, paid.status)
        assertEquals("SUCCESS", paid.paymentStatus)
        assertEquals(paymentId, paid.paymentId)
    }

    @Test
    fun `merchant cannot accept unpaid prepaid order`() {
        val merchantId = UUID.randomUUID()
        val order = placedOrder(paymentMethod = "UPI", paymentStatus = "PENDING")
        whenever(orderRepository.findByIdForUpdate(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(providerModule.ownerUserId(order.providerId)).thenReturn(merchantId)

        val error = assertThrows<IllegalStateException> {
            service.updateOrderStatusWithAuth(
                order.orderId!!,
                OrderStatus.ACCEPTED,
                merchantId,
                "MERCHANT",
                null,
            )
        }

        assertEquals(
            "Prepaid order cannot be accepted until server-verified payment succeeds",
            error.message,
        )
    }

    @Test
    fun `merchant cannot skip acceptance and move placed order to preparing`() {
        val merchantId = UUID.randomUUID()
        val order = placedOrder(paymentMethod = "COD", paymentStatus = "COD_PENDING")
        whenever(orderRepository.findByIdForUpdate(order.orderId!!)).thenReturn(Optional.of(order))
        whenever(providerModule.ownerUserId(order.providerId)).thenReturn(merchantId)

        val error = assertThrows<IllegalStateException> {
            service.updateOrderStatusWithAuth(
                order.orderId!!,
                OrderStatus.PREPARING,
                merchantId,
                "MERCHANT",
                null,
            )
        }

        assertEquals("Invalid order transition: PLACED -> PREPARING", error.message)
    }

    private fun placedOrder(paymentMethod: String, paymentStatus: String): Order = Order(
        orderId = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = OrderStatus.PLACED,
        subtotalAmount = BigDecimal("500.00"),
        deliveryFee = BigDecimal.ZERO,
        discountAmount = BigDecimal.ZERO,
        taxAmount = BigDecimal("25.00"),
        totalAmount = BigDecimal("525.00"),
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
    )
}
