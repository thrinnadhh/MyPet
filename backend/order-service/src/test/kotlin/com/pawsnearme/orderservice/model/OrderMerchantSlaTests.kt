package com.pawsnearme.orderservice.model

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class OrderMerchantSlaTests {
    private fun order(status: OrderStatus) = Order(
        orderId = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = status,
        subtotalAmount = BigDecimal("100.00"),
        totalAmount = BigDecimal("129.00"),
    )

    @Test
    fun `pre-update captures preparingAt only in PREPARING`() {
        val accepted = order(OrderStatus.ACCEPTED)
        accepted.capturePreparingTimestamp()
        assertNull(accepted.preparingAt)

        val preparing = order(OrderStatus.PREPARING)
        preparing.capturePreparingTimestamp()
        assertNotNull(preparing.preparingAt)
    }
}
