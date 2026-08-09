package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProviderAdminApprovalService(
    private val providerRepository: ProviderRepository,
    private val outboxService: OutboxService,
) {
    @Transactional
    fun approve(providerId: UUID, actorUserId: UUID): Provider {
        val provider = providerRepository.findByIdForUpdate(providerId).orElseThrow {
            IllegalArgumentException("Provider not found: $providerId")
        }
        if (provider.status != ProviderStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Provider must be in PENDING_APPROVAL status to approve")
        }

        val previousStatus = provider.status
        provider.status = ProviderStatus.ACTIVE
        val approved = providerRepository.save(provider)
        val eventId = UUID.randomUUID()
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "PROVIDER",
            aggregateId = requireNotNull(approved.providerId),
            eventType = "ProviderApproved",
            eventPayload = mapOf(
                "event_id" to eventId.toString(),
                "event_type" to "ProviderApproved",
                "occurred_at" to Instant.now().toString(),
                "actor_id" to actorUserId.toString(),
                "provider_id" to approved.providerId.toString(),
                "provider_type" to approved.providerType.name,
                "previous_status" to previousStatus.name,
                "new_status" to approved.status.name,
            ),
        )
        return approved
    }
}
