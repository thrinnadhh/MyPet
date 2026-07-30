package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface LoyaltyProgramRepository : JpaRepository<LoyaltyProgram, UUID> {
    fun findByProviderId(providerId: UUID): Optional<LoyaltyProgram>
    fun findByProviderIdIsNull(): Optional<LoyaltyProgram>
}

@Repository
interface CustomerLoyaltyAccountRepository : JpaRepository<CustomerLoyaltyAccount, UUID> {
    fun findByCustomerIdAndProviderId(customerId: UUID, providerId: UUID): Optional<CustomerLoyaltyAccount>
    fun findByCustomerId(customerId: UUID): List<CustomerLoyaltyAccount>
}

@Repository
interface LoyaltyLedgerEntryRepository : JpaRepository<LoyaltyLedgerEntry, UUID> {
    fun findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId: UUID, providerId: UUID): List<LoyaltyLedgerEntry>
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<LoyaltyLedgerEntry>
    fun findByReferenceId(referenceId: UUID): List<LoyaltyLedgerEntry>
}

@Repository
interface LoyaltyRewardInstanceRepository : JpaRepository<LoyaltyRewardInstance, UUID> {
    fun findByCode(code: String): Optional<LoyaltyRewardInstance>
    fun findByCustomerIdAndStatusIn(customerId: UUID, statuses: List<RewardStatus>): List<LoyaltyRewardInstance>
    fun findByCustomerIdAndProviderIdAndStatusIn(customerId: UUID, providerId: UUID, statuses: List<RewardStatus>): List<LoyaltyRewardInstance>
    fun findByOrderIdAndStatus(orderId: UUID, status: RewardStatus): Optional<LoyaltyRewardInstance>
}

@Repository
interface LoyaltyProcessedEventRepository : JpaRepository<LoyaltyProcessedEvent, UUID> {
    fun existsByEventTypeAndReferenceId(eventType: String, referenceId: UUID): Boolean
}

@Repository
interface LoyaltyAuditLogRepository : JpaRepository<LoyaltyAuditLog, UUID> {
    fun findByProviderIdOrderByCreatedAtDesc(providerId: UUID): List<LoyaltyAuditLog>
    fun findAllByOrderByCreatedAtDesc(): List<LoyaltyAuditLog>
}
