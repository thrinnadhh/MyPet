package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.OrderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID

class OrderControllerTests {

    private val orderService: OrderService = mock()
    private val orderRepository: OrderRepository = mock()
    private val controller = OrderController(orderService, orderRepository)

    @Test
    fun `confirmOrder - success returns 200 and accepted order`() {
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val order = Order(
            orderId = orderId,
            customerId = UUID.randomUUID(),
            providerId = UUID.randomUUID(),
            deliveryAddressId = UUID.randomUUID(),
            status = OrderStatus.ACCEPTED,
            subtotalAmount = BigDecimal("500.00"),
            totalAmount = BigDecimal("500.00"),
            paymentId = paymentId
        )
        whenever(orderService.confirmOrder(orderId, paymentId)).thenReturn(order)

        val response = controller.confirmOrder(orderId, paymentId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(order, response.body)
    }

    @Test
    fun `confirmOrder - validation failure throws IllegalStateException`() {
        val orderId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        whenever(orderService.confirmOrder(orderId, paymentId))
            .thenThrow(IllegalStateException("Payment verification failed"))

        assertThrows<IllegalStateException> {
            controller.confirmOrder(orderId, paymentId)
        }
    }
}
