package com.pawsnearme.dispatchservice.service

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.util.UUID

data class OrderDeliveryContact(
    val phoneNumber: String?,
    val verified: Boolean
)

@Component
class DeliveryContactLookup(
    private val entityManager: EntityManager
) {
    fun forOrder(orderId: UUID): OrderDeliveryContact {
        return try {
            val result = entityManager.createNativeQuery(
                """
                    SELECT delivery_contact_phone, delivery_contact_verified
                    FROM orders.orders
                    WHERE order_id = :orderId
                """.trimIndent()
            )
                .setParameter("orderId", orderId)
                .singleResult as Array<*>

            OrderDeliveryContact(
                phoneNumber = result[0] as? String,
                verified = result[1] as? Boolean ?: false
            )
        } catch (_: Exception) {
            OrderDeliveryContact(phoneNumber = null, verified = false)
        }
    }
}
