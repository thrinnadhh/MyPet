package com.pawsnearme.notificationservice.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderPlacedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String = "OrderPlaced",
    val orderId: UUID,
    val actorId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val merchantOwnerUserId: UUID? = null,
    val totalAmount: BigDecimal,
    val occurredAt: Instant = Instant.now(),
)

data class MerchantOrderActionableEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String = "MerchantOrderActionable",
    val orderId: UUID,
    val actorId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val merchantOwnerUserId: UUID? = null,
    val totalAmount: BigDecimal,
    val occurredAt: Instant = Instant.now(),
)

data class OrderStatusChangedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val orderId: UUID,
    val actorId: UUID,
    val customerId: UUID,
    val fromStatus: String,
    val toStatus: String,
    val totalAmount: BigDecimal,
    val deliveryFee: BigDecimal,
    val captainId: UUID? = null,
    val providerId: UUID? = null,
    val merchantOwnerUserId: UUID? = null,
    val occurredAt: Instant = Instant.now(),
)
