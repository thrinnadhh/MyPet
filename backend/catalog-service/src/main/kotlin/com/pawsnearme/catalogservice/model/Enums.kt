package com.pawsnearme.catalogservice.model

enum class OfferingStatus {
    ACTIVE, INACTIVE, OUT_OF_STOCK
}

enum class SlotStatus {
    AVAILABLE, HELD, BOOKED, BLOCKED
}

/** Mirrors provider-service FulfillmentType — kept in sync via shared schema contract. */
enum class FulfillmentType {
    DELIVERY, APPOINTMENT
}
