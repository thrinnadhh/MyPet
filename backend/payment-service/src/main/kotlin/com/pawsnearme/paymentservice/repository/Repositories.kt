package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.AppointmentRef
import com.pawsnearme.paymentservice.model.CaptainEarningRef
import com.pawsnearme.paymentservice.model.LinkedAccount
import com.pawsnearme.paymentservice.model.OrderRef
import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.model.PlatformCommissionLedger
import com.pawsnearme.paymentservice.model.Promotion
import com.pawsnearme.paymentservice.model.ProviderRef
import com.pawsnearme.paymentservice.model.Transaction
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findFirstByReferenceIdOrderByCreatedAtDesc(referenceId: UUID): Transaction?

    /**
     * Payment creation, reconciliation and refunds are check-then-act financial operations.
     * Transactional callers use this row lock to serialize state transitions for one reference.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
        referenceId: UUID,
        statuses: Collection<String>
    ): Transaction?

    /**
     * Webhook mutations serialize on the same row as refunds/reconciliation and may
     * never select a refund-terminal transaction. A delayed payment-success webhook
     * therefore cannot resurrect REFUND_PENDING or REFUNDED back to SUCCESS.
     */
    @Query(
        value = """
            SELECT *
            FROM payments.transactions
            WHERE gateway_transaction_id = :gatewayTransactionId
              AND status NOT IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')
            FOR UPDATE
        """,
        nativeQuery = true
    )
    fun findByGatewayTransactionId(@Param("gatewayTransactionId") gatewayTransactionId: String): Transaction?

    /**
     * Refund status reconciliation intentionally has a separate locked lookup because
     * payment-success webhooks must never be able to select refund lifecycle rows.
     */
    @Query(
        value = """
            SELECT *
            FROM payments.transactions
            WHERE gateway_transaction_id = :gatewayTransactionId
              AND status IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')
            FOR UPDATE
        """,
        nativeQuery = true
    )
    fun findRefundByGatewayTransactionId(@Param("gatewayTransactionId") gatewayTransactionId: String): Transaction?
}

@Repository
interface PayoutRepository : JpaRepository<Payout, UUID> {
    fun findByPayeeUserId(payeeUserId: UUID): List<Payout>
    fun findByRazorpayTransferId(razorpayTransferId: String): Payout?
    fun findByPayeeUserIdAndPayeeRoleAndPeriodStartAndPeriodEnd(
        payeeUserId: UUID,
        payeeRole: String,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): Payout?
}

@Repository
interface LinkedAccountRepository : JpaRepository<LinkedAccount, UUID> {
    fun findByPayeeUserId(payeeUserId: UUID): LinkedAccount?
    fun findByPayeeUserIdAndPayeeRole(payeeUserId: UUID, payeeRole: String): LinkedAccount?
}

@Repository
interface PlatformCommissionLedgerRepository : JpaRepository<PlatformCommissionLedger, UUID> {
    fun findByProviderId(providerId: UUID): List<PlatformCommissionLedger>
}

@Repository
interface PromotionRepository : JpaRepository<Promotion, UUID> {
    fun findByCode(code: String): Promotion?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.code = :code")
    fun findByCodeForUpdate(@Param("code") code: String): Promotion?

    fun findByProviderId(providerId: UUID): List<Promotion>
    fun findByProviderIdIsNull(): List<Promotion>
    fun existsByCode(code: String): Boolean
}

@Repository
interface OrderRefRepository : JpaRepository<OrderRef, UUID> {
    @Query("""
        SELECT o.providerId, p.ownerUserId, SUM(o.totalAmount)
        FROM OrderRef o
        JOIN PaymentProviderRef p ON p.providerId = o.providerId
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

    fun findByStatusAndDeliveredAtBetween(status: String, start: Instant, end: Instant): List<OrderRef>
    fun findByProviderIdAndStatus(providerId: UUID, status: String): List<OrderRef>
}

@Repository
interface AppointmentRefRepository : JpaRepository<AppointmentRef, UUID> {
    @Query("""
        SELECT a.providerId, p.ownerUserId, SUM(a.priceAmount)
        FROM AppointmentRef a
        JOIN PaymentProviderRef p ON p.providerId = a.providerId
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

    fun findByProviderIdAndStatus(providerId: UUID, status: String): List<AppointmentRef>
}

@Repository
interface CaptainEarningRefRepository : JpaRepository<CaptainEarningRef, UUID> {
    @Query("""
        SELECT c.captainId, SUM(c.amount)
        FROM CaptainEarningRef c
        WHERE c.payoutId IS NULL
          AND c.earnedAt >= :start
          AND c.earnedAt < :end
        GROUP BY c.captainId
    """)
    fun sumAmountByCaptainAndPeriod(start: Instant, end: Instant): List<Array<Any>>

    fun findByPayoutIdIsNullAndEarnedAtBetweenAndCaptainId(
        start: Instant,
        end: Instant,
        captainId: UUID
    ): List<CaptainEarningRef>
}

@Repository
interface ProviderRefRepository : JpaRepository<ProviderRef, UUID>
