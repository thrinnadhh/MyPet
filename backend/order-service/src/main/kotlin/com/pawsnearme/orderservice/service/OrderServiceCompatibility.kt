package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.OrderItem
import java.util.UUID

/**
 * Source-compatible overload retained for focused legacy tests and rollback
 * tooling that still passes the former catalog base URL argument.
 */
@Suppress("UNUSED_PARAMETER")
fun OrderService.decrementCatalogStockFallback(
    offeringId: UUID,
    quantity: Int,
    legacyCatalogBaseUrl: String,
    error: Throwable
): OrderItem = decrementCatalogStockFallback(offeringId, quantity, error)
