package com.pawsnearme.orderservice.service

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.util.UUID

data class CustomerDeliveryContact(
    val phoneNumber: String,
    /** Address contact existence does not prove authenticated ownership of the phone number. */
    val verified: Boolean = false,
)

@Component
class DeliveryContactLookup(
    private val entityManager: EntityManager,
) {
    /**
     * Resolve the contact only when both the address and the contact belong to the
     * authenticated customer. The mobile client is not a trust boundary for the
     * phone snapshot stored on an order.
     */
    fun forCustomerAddress(customerId: UUID, addressId: UUID): CustomerDeliveryContact? {
        val rows = entityManager.createNativeQuery(
            """
                SELECT dc.phone_number
                FROM identity.delivery_contacts dc
                INNER JOIN identity.addresses a ON a.address_id = dc.address_id
                WHERE dc.address_id = :addressId
                  AND dc.user_id = :customerId
                  AND a.user_id = :customerId
                LIMIT 1
            """.trimIndent()
        )
            .setParameter("addressId", addressId)
            .setParameter("customerId", customerId)
            .resultList

        val phone = rows.firstOrNull() as? String ?: return null
        return CustomerDeliveryContact(phoneNumber = phone)
    }
}
