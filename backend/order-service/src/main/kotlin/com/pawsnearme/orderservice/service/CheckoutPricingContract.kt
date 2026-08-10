// GENERATED FROM contracts/checkout-pricing.json. DO NOT EDIT BY HAND.
package com.pawsnearme.orderservice.service

import java.math.BigDecimal

object CheckoutPricingContract {
    val TAX_RATE: BigDecimal = BigDecimal("0.05")
    val BASE_DELIVERY_FEE: BigDecimal = BigDecimal("29.00")
    val INCLUDED_DISTANCE_KM: BigDecimal = BigDecimal("2.0")
    val PER_KM_FEE: BigDecimal = BigDecimal("8.00")
    val MAX_SERVICE_DISTANCE_KM: BigDecimal = BigDecimal("25.0")
    val ROUTE_DISTANCE_FACTOR: BigDecimal = BigDecimal("1.20")
}
