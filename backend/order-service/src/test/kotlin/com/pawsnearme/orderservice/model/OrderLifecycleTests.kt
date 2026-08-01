package com.pawsnearme.orderservice.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderLifecycleTests {
    @Test
    fun `accepted COD order receives accepted timestamp before insert`() {
        val placedAt = Instant.parse("2026-08-01T10:49:50Z")
        val order = order(OrderStatus.ACCEPTED, placedAt)

        order.alignLifecycleTimestamps()

        assertEquals(placedAt, order.acceptedAt)
    }

    @Test
    fun `placed online order does not receive accepted timestamp`() {
        val order = order(OrderStatus.PLACED, Instant.now())

        order.alignLifecycleTimestamps()

        assertNull(order.acceptedAt)
    }

    @Test
    fun `existing accepted timestamp is not overwritten`() {
        val placedAt = Instant.parse("2026-08-01T10:49:50Z")
        val acceptedAt = placedAt.plusSeconds(30)
        val order = order(OrderStatus.ACCEPTED, placedAt).apply { this.acceptedAt = acceptedAt }

        order.alignLifecycleTimestamps()

        assertNotNull(order.acceptedAt)
        assertEquals(acceptedAt, order.acceptedAt)
    }

    private fun order(status: OrderStatus, placedAt: Instant) = Order(
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = status,
        subtotalAmount = BigDecimal("199.00"),
        totalAmount = BigDecimal("257.95"),
        paymentMethod = if (status == OrderStatus.ACCEPTED) "COD" else "CARD",
        paymentStatus = if (status == OrderStatus.ACCEPTED) "COD_PENDING" else "PENDING",
        placedAt = placedAt
    )
}
