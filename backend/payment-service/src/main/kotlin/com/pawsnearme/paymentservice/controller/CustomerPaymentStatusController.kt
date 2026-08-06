package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.repository.TransactionRepository
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CustomerPaymentStatusView(
    val transactionId: UUID,
    val referenceId: UUID,
    val transactionType: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Service
class CustomerPaymentStatusService(
    private val transactionRepository: TransactionRepository,
    private val cashfreeGatewayService: CashfreeGatewayService,
) {
    @Transactional(readOnly = true)
    fun latestForReference(
        referenceId: UUID,
        requesterId: String?,
        requesterRole: String?,
    ): CustomerPaymentStatusView {
        val transaction = ownedTransaction(referenceId, requesterId, requesterRole)
        return transaction.toStatusView()
    }

    fun reconcileForReference(
        referenceId: UUID,
        requesterId: String?,
        requesterRole: String?,
    ): CustomerPaymentStatusView {
        val transaction = ownedTransaction(referenceId, requesterId, requesterRole)
        if (transaction.gateway != "CASHFREE") {
            throw IllegalStateException("Payment transaction is not configured for Cashfree")
        }
        cashfreeGatewayService.reconcile(referenceId)
        return ownedTransaction(referenceId, requesterId, requesterRole).toStatusView()
    }

    private fun ownedTransaction(referenceId: UUID, requesterId: String?, requesterRole: String?) =
        transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId)
            ?.also { transaction ->
                if (requesterRole != "ADMIN" && requesterId != transaction.userId.toString()) {
                    throw PaymentAccessDeniedException("Access denied for payment status")
                }
            }
            ?: throw NoSuchElementException("Payment transaction not found for reference ID $referenceId")

    private fun com.pawsnearme.paymentservice.model.Transaction.toStatusView() = CustomerPaymentStatusView(
        transactionId = requireNotNull(transactionId),
        referenceId = referenceId,
        transactionType = transactionType,
        amount = amount,
        currency = currency,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@RestController
@RequestMapping("/api/v1/payments/transactions/reference")
class CustomerPaymentStatusController(
    private val paymentStatusService: CustomerPaymentStatusService,
) {
    @GetMapping("/{referenceId}")
    fun getLatestForReference(
        @PathVariable referenceId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<CustomerPaymentStatusView> = ResponseEntity.ok(
        paymentStatusService.latestForReference(referenceId, xUserId, xUserRole),
    )

    @PostMapping("/{referenceId}/reconcile")
    fun reconcileLatestForReference(
        @PathVariable referenceId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
    ): ResponseEntity<CustomerPaymentStatusView> = ResponseEntity.ok(
        paymentStatusService.reconcileForReference(referenceId, xUserId, xUserRole),
    )
}
