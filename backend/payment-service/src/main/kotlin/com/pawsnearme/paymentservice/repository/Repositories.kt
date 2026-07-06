package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findFirstByReferenceIdOrderByCreatedAtDesc(referenceId: UUID): Transaction?
    fun findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(referenceId: UUID, statuses: Collection<String>): Transaction?
    fun findByGatewayTransactionId(gatewayTransactionId: String): Transaction?
}

@Repository
interface PayoutRepository : JpaRepository<Payout, UUID> {
    fun findByPayeeUserId(payeeUserId: UUID): List<Payout>
    fun findByPayeeUserIdAndPayeeRoleAndPeriodStartAndPeriodEnd(
        payeeUserId: UUID,
        payeeRole: String,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): Payout?
    fun findByRazorpayTransferId(razorpayTransferId: String): Payout?
}

@Repository
interface PromotionRepository : JpaRepository<Promotion, UUID> {
    fun findByCode(code: String): Promotion?
    fun findByProviderId(providerId: UUID): List<Promotion>
    fun findByProviderIdIsNull(): List<Promotion>
    fun existsByCode(code: String): Boolean
}

@Repository
interface OrderRefRepository : JpaRepository<OrderRef, UUID> {
    /**
     * Returns (ownerUserId, sumTotalAmount) grouped by provider owner — DB-level aggregation
     * to avoid loading all orders into JVM memory.
     */
    @Query("""
        SELECT o.providerId, p.ownerUserId, SUM(o.totalAmount)
        FROM OrderRef o
        JOIN ProviderRef p ON p.providerId = o.providerId
        WHERE o.status = :status
          AND o.deliveredAt >= :start
          AND o.deliveredAt < :end
        GROUP BY o.providerId, p.ownerUserId
    """)
    fun sumTotalAmountByOwnerAndPeriod(
        status: String,
        start: Instant,
        end: Instant
    ): List<Array<Any>>
}

@Repository
interface AppointmentRefRepository : JpaRepository<AppointmentRef, UUID> {
    /**
     * Returns (ownerUserId, sumPriceAmount) grouped by provider owner — DB-level aggregation.
     */
    @Query("""
        SELECT a.providerId, p.ownerUserId, SUM(a.priceAmount)
        FROM AppointmentRef a
        JOIN ProviderRef p ON p.providerId = a.providerId
        WHERE a.status = :status
          AND a.completedAt >= :start
          AND a.completedAt < :end
        GROUP BY a.providerId, p.ownerUserId
    """)
    fun sumPriceAmountByOwnerAndPeriod(
        status: String,
        start: Instant,
        end: Instant
    ): List<Array<Any>>
}

@Repository
interface CaptainEarningRefRepository : JpaRepository<CaptainEarningRef, UUID> {
    /**
     * Returns (captainId, sumAmount) grouped by captain — DB-level aggregation.
     */
    @Query("""
        SELECT c.captainId, SUM(c.amount)
        FROM CaptainEarningRef c
        WHERE c.payoutId IS NULL
          AND c.earnedAt >= :start
          AND c.earnedAt < :end
        GROUP BY c.captainId
    """)
    fun sumAmountByCaptainAndPeriod(start: Instant, end: Instant): List<Array<Any>>

    /** Used to bulk-link earnings to a payout after creation. */
    fun findByPayoutIdIsNullAndEarnedAtBetweenAndCaptainId(
        start: Instant,
        end: Instant,
        captainId: UUID
    ): List<CaptainEarningRef>
}

@Repository
interface ProviderRefRepository : JpaRepository<ProviderRef, UUID>

@Repository
interface LinkedAccountRepository : JpaRepository<LinkedAccount, UUID>

@Repository
interface PlatformCommissionLedgerRepository : JpaRepository<PlatformCommissionLedger, UUID> {
    fun findByProviderIdAndPeriodStartAndPeriodEnd(
        providerId: UUID,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): PlatformCommissionLedger?
}
