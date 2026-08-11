package com.pawsnearme.paymentservice.service

import com.pawsnearme.common.module.PaymentTransactionSnapshot
import com.pawsnearme.common.module.PrepareOrderPaymentCommand
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentLifecycleEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val transactionId: UUID,
    val referenceId: UUID,
    val transactionType: String,
    val actorId: UUID,
    val amount: BigDecimal,
    val gateway: String,
    val gatewayTransactionId: String?,
    val reason: String? = null,
    val occurredAt: Instant = Instant.now(),
)

@Service
class OrderPaymentLifecycleService(
    private val transactionRepository: TransactionRepository,
    private val outboxService: OutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun prepare(command: PrepareOrderPaymentCommand): PaymentTransactionSnapshot {
        val existing = transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(command.orderId)
        if (existing != null) {
            require(existing.userId == command.customerId) { "Existing order payment belongs to another customer" }
            require(existing.transactionType == "ORDER_PAYMENT") { "Existing transaction is not an order payment" }
            require(existing.amount.compareTo(command.amount) == 0) { "Existing order payment amount does not match checkout total" }
            if (existing.status in setOf("PENDING", "SUCCESS")) return existing.toSnapshot()
        }

        return transactionRepository.saveAndFlush(
            Transaction(
                userId = command.customerId,
                transactionType = "ORDER_PAYMENT",
                referenceId = command.orderId,
                amount = command.amount,
                status = "PENDING",
                gateway = "CASHFREE",
            )
        ).toSnapshot()
    }

    @Transactional
    fun capture(transaction: Transaction): Transaction {
        if (transaction.status == "SUCCESS") return transaction
        if (transaction.status in setOf("REFUNDED", "REFUND_PENDING")) {
            throw IllegalStateException("Refunded payment cannot be captured again")
        }
        transaction.status = "SUCCESS"
        val saved = transactionRepository.saveAndFlush(transaction)
        publish(saved, "PaymentCaptured", null)
        return saved
    }

    @Transactional
    fun fail(transaction: Transaction, reason: String, eventType: String = "PaymentFailed"): Transaction {
        if (transaction.status == "SUCCESS") {
            logger.warn("Ignoring {} for already-successful transaction {}", eventType, transaction.transactionId)
            return transaction
        }
        if (transaction.status in setOf("FAILED", "EXPIRED")) return transaction
        transaction.status = if (eventType == "PaymentExpired") "EXPIRED" else "FAILED"
        val saved = transactionRepository.saveAndFlush(transaction)
        publish(saved, eventType, reason)
        return saved
    }

    @Transactional
    fun expireOrderPayment(orderId: UUID, reason: String): PaymentTransactionSnapshot? {
        val transaction = transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(orderId) ?: return null
        if (transaction.transactionType != "ORDER_PAYMENT") return transaction.toSnapshot()
        if (transaction.status == "PENDING") {
            return fail(transaction, reason, "PaymentExpired").toSnapshot()
        }
        return transaction.toSnapshot()
    }

    @Transactional
    fun publishRefundState(transaction: Transaction): Transaction {
        when (transaction.status) {
            "REFUND_PENDING" -> publish(transaction, "PaymentRefundPending", "Cashfree refund is pending")
            "REFUNDED" -> publish(transaction, "PaymentRefunded", "Cashfree refund completed")
            else -> throw IllegalStateException("Cannot publish refund lifecycle for payment status ${transaction.status}")
        }
        return transaction
    }

    private fun publish(transaction: Transaction, eventType: String, reason: String?) {
        val transactionId = requireNotNull(transaction.transactionId) { "Payment transaction ID is missing" }
        val event = PaymentLifecycleEvent(
            eventType = eventType,
            transactionId = transactionId,
            referenceId = transaction.referenceId,
            transactionType = transaction.transactionType,
            actorId = transaction.userId,
            amount = transaction.amount,
            gateway = transaction.gateway,
            gatewayTransactionId = transaction.gatewayTransactionId,
            reason = reason,
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "PAYMENT",
            aggregateId = transaction.referenceId,
            eventType = event.eventType,
            eventPayload = event,
        )
    }

    private fun Transaction.toSnapshot() = PaymentTransactionSnapshot(
        transactionId = requireNotNull(transactionId),
        userId = userId,
        referenceId = referenceId,
        transactionType = transactionType,
        amount = amount,
        status = status,
    )
}
