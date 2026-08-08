package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.AdminTransactionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class PaymentAdminPage(
    val content: List<Transaction>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@Service
class PaymentAdminQueryService(
    private val repository: AdminTransactionRepository
) {
    @Transactional(readOnly = true)
    fun search(
        referenceId: UUID?,
        gatewayTransactionId: String?,
        status: String?,
        fromTime: Instant?,
        toTime: Instant?,
        page: Int,
        size: Int
    ): PaymentAdminPage {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        if (fromTime != null && toTime != null) require(fromTime < toTime) { "fromTime must be before toTime" }

        val gatewayId = gatewayTransactionId?.trim()?.takeIf(String::isNotEmpty)
        if (gatewayId != null) {
            val transaction = repository.findByGatewayTransactionId(gatewayId)
            val filtered = transaction?.takeIf {
                (referenceId == null || it.referenceId == referenceId) &&
                    (status.isNullOrBlank() || it.status.equals(status.trim(), ignoreCase = true)) &&
                    (fromTime == null || !it.createdAt.isBefore(fromTime)) &&
                    (toTime == null || it.createdAt.isBefore(toTime))
            }
            return PaymentAdminPage(
                content = listOfNotNull(filtered),
                page = 0,
                size = size,
                totalElements = if (filtered == null) 0 else 1,
                totalPages = if (filtered == null) 0 else 1
            )
        }

        val result = repository.search(referenceId, status?.trim()?.takeIf(String::isNotEmpty), fromTime, toTime, PageRequest.of(page, size))
        return PaymentAdminPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }
}
