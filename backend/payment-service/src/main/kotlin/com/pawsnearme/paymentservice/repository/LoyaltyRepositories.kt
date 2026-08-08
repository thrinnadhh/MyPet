package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Modifying
    @Query(
        value = """
            INSERT INTO payments.customer_loyalty_accounts(account_id, customer_id, provider_id)
            VALUES (gen_random_uuid(), :customerId, :providerId)
            ON CONFLICT (customer_id, provider_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun ensureAccount(@Param("customerId") customerId: UUID, @Param("providerId") providerId: UUID): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select a from CustomerLoyaltyAccount a where a.customerId = :customerId and a.providerId = :providerId"
    )
    fun findByCustomerIdAndProviderIdForUpdate(
        @Param("customerId") customerId: UUID,
        @Param("providerId") providerId: UUID
    ): Optional<CustomerLoyaltyAccount>
}

@Repository
interface LoyaltyLedgerEntryRepository : JpaRepository<LoyaltyLedgerEntry, UUID> {
    fun findByCustomerIdAndProviderIdOrderByCreatedAtDesc(customerId: UUID, providerId: UUID): List<LoyaltyLedgerEntry>
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<LoyaltyLedgerEntry>
    fun findByReferenceId(referenceId: UUID): List<LoyaltyLedgerEntry>
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID, pageable: Pageable): Page<LoyaltyLedgerEntry>
}

@Repository
interface LoyaltyRewardInstanceRepository : JpaRepository<LoyaltyRewardInstance, UUID> {
    fun findByCode(code: String): Optional<LoyaltyRewardInstance>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LoyaltyRewardInstance r where r.code = :code")
    fun findByCodeForUpdate(@Param("code") code: String): Optional<LoyaltyRewardInstance>

    fun findByCustomerIdAndStatusIn(customerId: UUID, statuses: List<RewardStatus>): List<LoyaltyRewardInstance>
    fun findByCustomerIdAndProviderIdAndStatusIn(customerId: UUID, providerId: UUID, statuses: List<RewardStatus>): List<LoyaltyRewardInstance>
    fun findByOrderIdAndStatus(orderId: UUID, status: RewardStatus): Optional<LoyaltyRewardInstance>
}

@Repository
interface LoyaltyProcessedEventRepository : JpaRepository<LoyaltyProcessedEvent, UUID> {
    fun existsByEventTypeAndReferenceId(eventType: String, referenceId: UUID): Boolean

    @Modifying
    @Query(
        value = """
            INSERT INTO payments.loyalty_processed_events(processed_id, event_type, reference_id)
            VALUES (gen_random_uuid(), :eventType, :referenceId)
            ON CONFLICT (event_type, reference_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("eventType") eventType: String,
        @Param("referenceId") referenceId: UUID
    ): Int
}

@Repository
interface LoyaltyAuditLogRepository : JpaRepository<LoyaltyAuditLog, UUID> {
    fun findByProviderIdOrderByCreatedAtDesc(providerId: UUID): List<LoyaltyAuditLog>
    fun findAllByOrderByCreatedAtDesc(): List<LoyaltyAuditLog>
}
