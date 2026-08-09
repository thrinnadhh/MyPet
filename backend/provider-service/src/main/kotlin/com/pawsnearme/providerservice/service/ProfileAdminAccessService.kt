package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.Profile
import com.pawsnearme.providerservice.model.UserRole
import com.pawsnearme.providerservice.repository.ProfileRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProfileAdminAccessService(
    private val profileRepository: ProfileRepository,
    private val redisTemplate: StringRedisTemplate,
    private val outboxService: OutboxService,
) {
    @Transactional
    fun revoke(targetUserId: UUID, actorUserId: UUID, reason: String): Profile =
        changeAccess(targetUserId, actorUserId, reason, suspended = true)

    @Transactional
    fun restore(targetUserId: UUID, actorUserId: UUID, reason: String): Profile =
        changeAccess(targetUserId, actorUserId, reason, suspended = false)

    private fun changeAccess(
        targetUserId: UUID,
        actorUserId: UUID,
        reason: String,
        suspended: Boolean,
    ): Profile {
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..500) {
            "An administrative access-change reason between 3 and 500 characters is required"
        }
        require(targetUserId != actorUserId) {
            "Administrators cannot change their own access through customer access controls"
        }

        val profile = profileRepository.findByIdForUpdate(targetUserId).orElseThrow {
            IllegalArgumentException("Profile with ID $targetUserId not found")
        }
        require(profile.role != UserRole.ADMIN) {
            "Administrator identities cannot be changed through customer access controls"
        }

        // Idempotent retries are a no-op: one logical state change produces one audit event.
        if (profile.suspended == suspended) {
            synchronizeRevocationCache(targetUserId, suspended)
            return profile
        }

        val previousSuspended = profile.suspended
        profile.suspended = suspended
        val updated = profileRepository.save(profile)
        synchronizeRevocationCache(targetUserId, suspended)

        val eventId = UUID.randomUUID()
        val eventType = if (suspended) "CustomerAccessRevoked" else "CustomerAccessRestored"
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "PROFILE",
            aggregateId = targetUserId,
            eventType = eventType,
            eventPayload = mapOf(
                "event_id" to eventId.toString(),
                "event_type" to eventType,
                "occurred_at" to Instant.now().toString(),
                "actor_id" to actorUserId.toString(),
                "target_user_id" to targetUserId.toString(),
                "previous_suspended" to previousSuspended,
                "new_suspended" to suspended,
                "reason" to normalizedReason,
            ),
        )
        return updated
    }

    private fun synchronizeRevocationCache(userId: UUID, suspended: Boolean) {
        val key = "suspended_user:$userId"
        if (suspended) {
            redisTemplate.opsForValue().set(key, "true")
        } else {
            redisTemplate.delete(key)
        }
    }
}
