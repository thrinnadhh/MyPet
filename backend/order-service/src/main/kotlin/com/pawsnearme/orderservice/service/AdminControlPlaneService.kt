package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.AdminAuditLog
import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.SystemConfig
import com.pawsnearme.orderservice.repository.AdminAuditLogRepository
import com.pawsnearme.orderservice.repository.DisputeRepository
import com.pawsnearme.orderservice.repository.SystemConfigRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AdminDisputePage(
    val content: List<Dispute>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@Service
class AdminControlPlaneService(
    private val disputeRepository: DisputeRepository,
    private val systemConfigRepository: SystemConfigRepository,
    private val paymentModule: PaymentModuleApi,
    private val auditRepository: AdminAuditLogRepository,
    private val outboxService: OutboxService
) {
    @Transactional(readOnly = true)
    fun getDisputeRefundMode(): String = systemConfigRepository.findById(REFUND_MODE_KEY)
        .map { it.configValue }
        .orElse("MANUAL")

    @Transactional
    fun updateDisputeRefundMode(
        requestedMode: String,
        actorId: UUID,
        reason: String,
        traceId: String
    ): String {
        val mode = requestedMode.trim().uppercase()
        require(mode in ALLOWED_REFUND_MODES) { "Invalid mode. Allowed: MANUAL, AUTOMATED" }
        val safeReason = requireAuditReason(reason)
        val config = systemConfigRepository.findById(REFUND_MODE_KEY)
            .orElseGet { SystemConfig(REFUND_MODE_KEY, "MANUAL") }
        val previous = config.configValue

        if (previous == mode) return mode

        config.configValue = mode
        config.updatedAt = Instant.now()
        systemConfigRepository.save(config)
        auditRepository.save(
            AdminAuditLog(
                adminUserId = actorId,
                action = "DISPUTE_REFUND_MODE_UPDATED",
                entityType = "SYSTEM_CONFIG",
                entityId = REFUND_MODE_KEY,
                previousValue = previous,
                newValue = mode,
                reason = safeReason,
                traceId = normalizedTraceId(traceId)
            )
        )
        publishAdminEvent(
            eventType = "DisputeRefundModeUpdated",
            aggregateId = actorId,
            payload = mapOf(
                "actor_id" to actorId.toString(),
                "previous_mode" to previous,
                "new_mode" to mode,
                "reason" to safeReason
            )
        )
        return mode
    }

    @Transactional(readOnly = true)
    fun listDisputes(page: Int, size: Int): AdminDisputePage {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val result = disputeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return AdminDisputePage(
            content = result.content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional
    fun resolveDispute(
        disputeId: UUID,
        requestedDecision: String,
        resolutionNotes: String?,
        actorId: UUID,
        traceId: String
    ): Dispute {
        val decision = requestedDecision.trim().uppercase()
        require(decision in ALLOWED_DISPUTE_DECISIONS) {
            "Invalid dispute decision. Allowed: RESOLVED, REJECTED"
        }
        val notes = requireResolutionNotes(resolutionNotes.orEmpty())
        val dispute = disputeRepository.findByIdForUpdate(disputeId)
            .orElseThrow { NoSuchElementException("Dispute not found for ID $disputeId") }
        if (dispute.status != "OPEN") {
            throw IllegalStateException("Dispute is already resolved")
        }

        val refundMode = getDisputeRefundMode()
        if (decision == "RESOLVED" && refundMode == "AUTOMATED") {
            // PaymentModule is idempotent and its qualifying transaction lookup is row-locked.
            // Propagate failures: the Admin UI must never show RESOLVED when the configured
            // automated refund could not be issued.
            paymentModule.refundOrder(dispute.orderId)
        }

        val previous = dispute.status
        dispute.status = decision
        dispute.resolutionNotes = notes
        dispute.resolvedAt = Instant.now()
        val saved = disputeRepository.save(dispute)

        auditRepository.save(
            AdminAuditLog(
                adminUserId = actorId,
                action = "DISPUTE_DECIDED",
                entityType = "DISPUTE",
                entityId = disputeId.toString(),
                previousValue = previous,
                newValue = decision,
                reason = notes.take(500),
                traceId = normalizedTraceId(traceId)
            )
        )
        publishAdminEvent(
            eventType = "DisputeDecided",
            aggregateId = disputeId,
            payload = mapOf(
                "actor_id" to actorId.toString(),
                "dispute_id" to disputeId.toString(),
                "order_id" to dispute.orderId.toString(),
                "decision" to decision,
                "refund_mode" to refundMode,
                "resolution_notes" to notes
            )
        )
        return saved
    }

    private fun requireAuditReason(value: String): String {
        val normalized = value.trim()
        require(normalized.length in 3..500) { "A reason between 3 and 500 characters is required" }
        return normalized
    }

    private fun requireResolutionNotes(value: String): String {
        val normalized = value.trim()
        require(normalized.length in 3..4000) { "Resolution notes must contain between 3 and 4000 characters" }
        return normalized
    }

    private fun normalizedTraceId(traceId: String): String =
        traceId.trim().take(160).ifBlank { UUID.randomUUID().toString() }

    private fun publishAdminEvent(eventType: String, aggregateId: UUID, payload: Map<String, String>) {
        outboxService.saveEvent(
            aggregateType = "ADMIN_OPERATION",
            aggregateId = aggregateId,
            eventType = eventType,
            eventPayload = payload
        )
    }

    companion object {
        private const val REFUND_MODE_KEY = "dispute_refund_mode"
        private val ALLOWED_REFUND_MODES = setOf("MANUAL", "AUTOMATED")
        private val ALLOWED_DISPUTE_DECISIONS = setOf("RESOLVED", "REJECTED")
    }
}
