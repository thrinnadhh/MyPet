package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.EmailDelivery
import com.pawsnearme.notificationservice.repository.EmailDeliveryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AdminEmailDeliveryView(
    val emailDeliveryId: UUID,
    val userId: UUID?,
    val recipientEmailMasked: String,
    val templateCode: String,
    val provider: String?,
    val providerMessageId: String?,
    val status: String,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sentAt: Instant?,
)

data class AdminEmailDeliveryPage(
    val content: List<AdminEmailDeliveryView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@Service
class NotificationAdminService(
    private val deliveryRepository: EmailDeliveryRepository,
) {
    @Transactional(readOnly = true)
    fun list(page: Int, size: Int, status: String?): AdminEmailDeliveryPage {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val pageable = PageRequest.of(page, size)
        val normalizedStatus = status?.trim()?.uppercase()?.takeIf(String::isNotBlank)
        if (normalizedStatus != null) {
            require(normalizedStatus in VISIBLE_STATUSES) { "Unsupported delivery status filter" }
        }
        val result = if (normalizedStatus == null) {
            deliveryRepository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            deliveryRepository.findByStatusOrderByCreatedAtDesc(normalizedStatus, pageable)
        }
        return AdminEmailDeliveryPage(
            content = result.content.map(::toView),
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    /**
     * Schedules exactly one additional attempt for a definitively FAILED delivery.
     * UNKNOWN is deliberately excluded because the provider may already have accepted
     * the original request and retrying it could duplicate customer email.
     */
    @Transactional
    fun retryFailed(deliveryId: UUID): AdminEmailDeliveryView {
        val delivery = deliveryRepository.findByEmailDeliveryId(deliveryId)
            ?: throw NoSuchElementException("Email delivery not found: $deliveryId")
        require(delivery.status == "FAILED") {
            "Only definitively FAILED email deliveries can be retried manually"
        }
        delivery.status = "RETRY"
        delivery.attemptCount = (delivery.attemptCount - 1).coerceAtLeast(0)
        delivery.nextAttemptAt = Instant.now()
        delivery.lastError = "Manual admin retry scheduled after: ${delivery.lastError.orEmpty()}".take(1000)
        delivery.updatedAt = Instant.now()
        return toView(deliveryRepository.save(delivery))
    }

    private fun toView(delivery: EmailDelivery) = AdminEmailDeliveryView(
        emailDeliveryId = delivery.emailDeliveryId,
        userId = delivery.userId,
        recipientEmailMasked = maskEmail(delivery.recipientEmail),
        templateCode = delivery.templateCode,
        provider = delivery.provider,
        providerMessageId = delivery.providerMessageId,
        status = delivery.status,
        attemptCount = delivery.attemptCount,
        nextAttemptAt = delivery.nextAttemptAt,
        lastError = delivery.lastError,
        createdAt = delivery.createdAt,
        updatedAt = delivery.updatedAt,
        sentAt = delivery.sentAt,
    )

    private fun maskEmail(email: String): String {
        val local = email.substringBefore('@')
        val domain = email.substringAfter('@', missingDelimiterValue = "")
        if (domain.isBlank()) return "***"
        val visible = local.take(2)
        return "$visible***@$domain"
    }

    companion object {
        private val VISIBLE_STATUSES = setOf("PENDING", "RETRY", "SENT", "FAILED", "UNKNOWN")
    }
}
