package com.pawsnearme.orderservice.service

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.util.UUID

data class CustomerDeliveryAddressSnapshot(
    val addressId: UUID,
    val city: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
)

@Component
class CustomerDeliveryAddressLookup(
    private val entityManager: EntityManager,
) {
    fun forCustomerAddress(customerId: UUID, addressId: UUID): CustomerDeliveryAddressSnapshot? {
        val row = entityManager.createNativeQuery(
            """
                SELECT address_id, city, pincode, geo_lat, geo_lng
                FROM identity.addresses
                WHERE address_id = :addressId AND user_id = :customerId
                LIMIT 1
            """.trimIndent()
        )
            .setParameter("addressId", addressId)
            .setParameter("customerId", customerId)
            .resultList
            .firstOrNull() as? Array<*> ?: return null

        return CustomerDeliveryAddressSnapshot(
            addressId = row[0] as UUID,
            city = row[1] as String,
            pincode = row[2] as String,
            latitude = (row[3] as Number).toDouble(),
            longitude = (row[4] as Number).toDouble(),
        )
    }
}
