package com.pawsnearme.orderservice.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderLifecycleTests {
    @Test
    fun `canonical order statuses contain no legacy reassigned state`() {
        val statuses = OrderStatus.entries.map { it.name }

        assertTrue(
            statuses == listOf(
                "PLACED",
                "ACCEPTED",
                "PREPARING",
                "READY_FOR_PICKUP",
                "ASSIGNED",
                "PICKED_UP",
                "DELIVERED",
                "COMPLETED",
                "REJECTED",
                "CANCELLED"
            )
        )
        assertFalse("REASSIGNED" in statuses)
    }

    @Test
    fun `canonical payment statuses are independent from order status`() {
        assertTrue(
            PaymentStatus.entries.map { it.name } == listOf(
                "PENDING",
                "SUCCESS",
                "FAILED",
                "COD_PENDING",
                "COD_COLLECTED",
                "REFUND_PENDING",
                "REFUNDED"
            )
        )
    }

    @Test
    fun `merchant must accept before preparing and preparing before ready`() {
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderActor.MERCHANT))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.PLACED, OrderStatus.READY_FOR_PICKUP, OrderActor.MERCHANT))
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderActor.MERCHANT))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderActor.MERCHANT))
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderActor.MERCHANT))
    }

    @Test
    fun `dispatch alone assigns a ready order`() {
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.ASSIGNED, OrderActor.DISPATCH))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.ASSIGNED, OrderActor.MERCHANT))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.ASSIGNED, OrderActor.CAPTAIN))
    }

    @Test
    fun `captain must pick up before delivery`() {
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderActor.CAPTAIN))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.ASSIGNED, OrderStatus.DELIVERED, OrderActor.CAPTAIN))
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, OrderActor.CAPTAIN))
    }

    @Test
    fun `system alone completes delivered order`() {
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderActor.SYSTEM))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderActor.MERCHANT))
    }

    @Test
    fun `customer cancellation is only from placed`() {
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.PLACED, OrderStatus.CANCELLED, OrderActor.CUSTOMER))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED, OrderActor.CUSTOMER))
        assertTrue(CanonicalOrderContract.canTransition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED, OrderActor.MERCHANT))
    }

    @Test
    fun `invalid lifecycle jumps are rejected`() {
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.ACCEPTED, OrderStatus.DELIVERED, OrderActor.MERCHANT))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.PLACED, OrderStatus.DELIVERED, OrderActor.MERCHANT))
        assertFalse(CanonicalOrderContract.canTransition(OrderStatus.COMPLETED, OrderStatus.PLACED, OrderActor.SYSTEM))
    }
}
