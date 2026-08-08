package com.pawsnearme.providerservice.repository

import com.pawsnearme.providerservice.model.DeliveryContact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeliveryContactRepository : JpaRepository<DeliveryContact, UUID> {
    fun findByUserId(userId: UUID): List<DeliveryContact>
}
