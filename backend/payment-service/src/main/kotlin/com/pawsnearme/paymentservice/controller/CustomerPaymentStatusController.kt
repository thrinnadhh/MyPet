package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
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
    val updatedAt: Instant
)

@Service
class CustomerPaymentStatusService(
    private val transactionRepository: TransactionRepository
) {
    @Transactional(readOnly = true)
    fun latestForReference(referenceId: UUID): CustomerPaymentStatusView {
        val transaction = transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId)
            ?: throw NoSuchElementException("Payment transaction not found for reference ID $referenceId")
        return CustomerPaymentStatusView(
            transactionId = requireNotNull(transaction.transactionId),
            referenceId = transaction.referenceId,
            transactionType = transaction.transactionType,
            amount = transaction.amount,
            currency = transaction.currency,
            status = transaction.status,
            createdAt = transaction.createdAt,
            updatedAt = transaction.updatedAt
        )
    }
}

@RestController
@RequestMapping("/api/v1/payments/transactions/reference")
class CustomerPaymentStatusController(
    private val paymentStatusService: CustomerPaymentStatusService
) {
    @GetMapping("/{referenceId}")
    fun getLatestForReference(
        @PathVariable referenceId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<CustomerPaymentStatusView> {
        val view = paymentStatusService.latestForReference(referenceId)
        if (xUserRole != "ADMIN" && xUserId != paymentOwner(view).toString()) {
            throw PaymentAccessDeniedException("Access denied for payment status")
        }
        return ResponseEntity.ok(view)
    }

    private fun paymentOwner(view: CustomerPaymentStatusView): UUID {
        // Owner is checked from the persisted transaction without exposing it in the DTO.
        return paymentStatusServiceOwner(view.transactionId)
    }

    private fun paymentStatusServiceOwner(transactionId: UUID): UUID =
        paymentStatusService.ownerForTransaction(transactionId)
}

@Transactional(readOnly = true)
fun CustomerPaymentStatusService.ownerForTransaction(transactionId: UUID): UUID =
    transactionRepository.findById(transactionId)
        .orElseThrow { NoSuchElementException("Payment transaction not found for ID $transactionId") }
        .userId
