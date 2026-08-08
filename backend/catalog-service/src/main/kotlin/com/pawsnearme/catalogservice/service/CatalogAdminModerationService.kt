package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.CatalogModerationAuditLog
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.repository.CatalogModerationAuditLogRepository
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.common.outbox.OutboxService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AdminOfferingPage(
    val content: List<Offering>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@Service
class CatalogAdminModerationService(
    private val offeringRepository: OfferingRepository,
    private val auditRepository: CatalogModerationAuditLogRepository,
    private val outboxService: OutboxService
) {
    @Transactional(readOnly = true)
    fun listOfferings(page: Int, size: Int): AdminOfferingPage {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val result = offeringRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return AdminOfferingPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional
    fun disable(offeringId: UUID, actorId: UUID, reason: String): Offering {
        val safeReason = requireReason(reason)
        val offering = offeringRepository.findByIdForUpdate(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
        if (offering.adminDisabled) {
            throw IllegalStateException("Offering is already disabled by an administrator")
        }
        val previousStatus = offering.status
        offering.adminDisabled = true
        offering.moderationReason = safeReason
        offering.moderatedByUserId = actorId
        offering.moderatedAt = Instant.now()
        offering.status = OfferingStatus.INACTIVE
        val saved = offeringRepository.save(offering)
        record(saved, actorId, "OFFERING_DISABLED", previousStatus, saved.status, safeReason)
        return saved
    }

    @Transactional
    fun restore(offeringId: UUID, actorId: UUID, reason: String): Offering {
        val safeReason = requireReason(reason)
        val offering = offeringRepository.findByIdForUpdate(offeringId)
            .orElseThrow { NoSuchElementException("Offering with ID $offeringId not found") }
        if (!offering.adminDisabled) {
            throw IllegalStateException("Offering is not administratively disabled")
        }
        val previousStatus = offering.status
        offering.adminDisabled = false
        offering.moderationReason = null
        offering.moderatedByUserId = actorId
        offering.moderatedAt = Instant.now()
        // Restoration makes the listing eligible again only at catalog level. Stock
        // still gates purchase; OUT_OF_STOCK is represented by quantity enforcement.
        offering.status = OfferingStatus.ACTIVE
        val saved = offeringRepository.save(offering)
        record(saved, actorId, "OFFERING_RESTORED", previousStatus, saved.status, safeReason)
        return saved
    }

    private fun record(
        offering: Offering,
        actorId: UUID,
        action: String,
        previous: OfferingStatus,
        current: OfferingStatus,
        reason: String
    ) {
        val offeringId = requireNotNull(offering.offeringId)
        auditRepository.save(
            CatalogModerationAuditLog(
                adminUserId = actorId,
                offeringId = offeringId,
                action = action,
                previousStatus = previous.name,
                newStatus = current.name,
                reason = reason
            )
        )
        val eventId = UUID.randomUUID()
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "OFFERING",
            aggregateId = offeringId,
            eventType = if (offering.adminDisabled) "OfferingModerated" else "OfferingRestored",
            eventPayload = mapOf(
                "event_id" to eventId.toString(),
                "actor_id" to actorId.toString(),
                "offering_id" to offeringId.toString(),
                "provider_id" to offering.providerId.toString(),
                "action" to action,
                "previous_status" to previous.name,
                "new_status" to current.name,
                "admin_disabled" to offering.adminDisabled,
                "reason" to reason,
                "occurred_at" to Instant.now().toString()
            )
        )
    }

    private fun requireReason(reason: String): String {
        val normalized = reason.trim()
        require(normalized.length in 3..500) { "A moderation reason between 3 and 500 characters is required" }
        return normalized
    }
}
