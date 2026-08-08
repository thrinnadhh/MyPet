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
class ProviderAdminLifecycleService(
    private val providerRepository: ProviderRepository,
    private val outboxService: OutboxService
) {
    @Transactional
    fun suspendProvider(providerId: UUID, actorId: UUID, reason: String): Provider {
        val safeReason = validateReason(reason)
        val provider = providerRepository.findByIdForUpdate(providerId)
            .orElseThrow { NoSuchElementException("Provider not found: $providerId") }
        if (provider.status != ProviderStatus.ACTIVE) {
            throw IllegalStateException("Only ACTIVE providers can be suspended. Current status: ${provider.status}")
        }
        return changeStatus(provider, ProviderStatus.SUSPENDED, actorId, safeReason, "ProviderSuspended")
    }

    @Transactional
    fun reactivateProvider(providerId: UUID, actorId: UUID, reason: String): Provider {
        val safeReason = validateReason(reason)
        val provider = providerRepository.findByIdForUpdate(providerId)
            .orElseThrow { NoSuchElementException("Provider not found: $providerId") }
        if (provider.status != ProviderStatus.SUSPENDED) {
            throw IllegalStateException("Only SUSPENDED providers can be reactivated. Current status: ${provider.status}")
        }
        return changeStatus(provider, ProviderStatus.ACTIVE, actorId, safeReason, "ProviderReactivated")
    }

    private fun changeStatus(
        provider: Provider,
        target: ProviderStatus,
        actorId: UUID,
        reason: String,
        eventType: String
    ): Provider {
        val previous = provider.status
        provider.status = target
        val saved = providerRepository.save(provider)
        val eventId = UUID.randomUUID()
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "PROVIDER",
            aggregateId = requireNotNull(saved.providerId),
            eventType = eventType,
            eventPayload = mapOf(
                "event_id" to eventId.toString(),
                "event_type" to eventType,
                "occurred_at" to Instant.now().toString(),
                "actor_id" to actorId.toString(),
                "provider_id" to saved.providerId.toString(),
                "previous_status" to previous.name,
                "new_status" to target.name,
                "reason" to reason
            )
        )
        return saved
    }

    private fun validateReason(reason: String): String {
        val normalized = reason.trim()
        require(normalized.length in 3..500) { "A reason between 3 and 500 characters is required" }
        return normalized
    }
}
