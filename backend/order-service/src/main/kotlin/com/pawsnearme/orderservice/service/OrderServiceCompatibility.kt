package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.OrderItem
import java.util.UUID

/**
 * Source-compatible overload retained for focused legacy tests and rollback
 * tooling that still passes the former catalog base URL argument.
 *
 * The legacy URL value is not an order identifier, so the compatibility path
 * receives an explicit deterministic scope. Production reservations are scoped
 * by quote token in OrderService.createOrder.
 */
@Suppress("UNUSED_PARAMETER")
fun OrderService.decrementCatalogStockFallback(
    offeringId: UUID,
    quantity: Int,
    legacyCatalogBaseUrl: String,
    error: Throwable
): OrderItem = decrementCatalogStockFallback(
    offeringId = offeringId,
    quantity = quantity,
    reservationScope = "legacy-fallback:$legacyCatalogBaseUrl",
    error = error,
)
