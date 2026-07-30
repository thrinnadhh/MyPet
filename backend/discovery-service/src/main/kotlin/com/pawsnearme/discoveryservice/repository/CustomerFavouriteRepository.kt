package com.pawsnearme.discoveryservice.repository

import com.pawsnearme.discoveryservice.model.CustomerFavourite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CustomerFavouriteRepository : JpaRepository<CustomerFavourite, UUID> {
    fun findAllByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<CustomerFavourite>
    fun findByCustomerIdAndTargetTypeAndTargetId(customerId: UUID, targetType: String, targetId: String): Optional<CustomerFavourite>
    fun deleteByCustomerIdAndTargetTypeAndTargetId(customerId: UUID, targetType: String, targetId: String)
}
