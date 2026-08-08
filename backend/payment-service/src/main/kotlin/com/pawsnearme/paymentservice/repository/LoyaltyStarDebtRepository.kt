package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.LoyaltyStarDebt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface LoyaltyStarDebtRepository : JpaRepository<LoyaltyStarDebt, UUID> {
    fun findByCustomerIdAndProviderId(customerId: UUID, providerId: UUID): Optional<LoyaltyStarDebt>
}
