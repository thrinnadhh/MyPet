package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.notificationservice.model.EmailDelivery
import com.pawsnearme.notificationservice.model.NotificationReferenceOwner
import com.pawsnearme.notificationservice.model.NotificationReferenceOwnerId
import com.pawsnearme.notificationservice.repository.EmailDeliveryRepository
import com.pawsnearme.notificationservice.repository.NotificationContactRepository
import com.pawsnearme.notificationservice.repository.NotificationReferenceOwnerRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

@Service
class TransactionalEmailService(
    private val deliveryRepository: EmailDeliveryRepository,
    private val contactRepository: NotificationContactRepository,
    private val referenceOwnerRepository: NotificationReferenceOwnerRepository,
    private val msg91Provider: Msg91TransactionalEmailProvider,
    private val brevoProvider: BrevoTransactionalEmailProvider,
    private val objectMapper: ObjectMapper,
    private val environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val variablesType = object : TypeReference<Map<String, Any?>>() {}

    fun registerReferenceOwner(referenceType: String, referenceId: UUID, userId: UUID) {
        val normalizedType = referenceType.trim().uppercase().take(40)
        val id = NotificationReferenceOwnerId(normalizedType, referenceId)
        val existing = referenceOwnerRepository.findById(id).orElse(null)
        if (existing == null) {
            referenceOwnerRepository.save(
                NotificationReferenceOwner(
                    referenceType = normalizedType,
                    referenceId = referenceId,
                    userId = userId,
                )
            )
        } else if (existing.userId != userId) {
            log.error(
                "Reference ownership mismatch for {} {}: existing={}, incoming={}",
                normalizedType,
                referenceId,
                existing.userId,
                userId,
            )
        }
    }

    fun enqueueForReference(
        referenceType: String,
        referenceId: UUID,
        templateCode: String,
        idempotencyKey: String,
        variables: Map<String, Any?>,
    ): EmailDelivery? {
        val owner = referenceOwnerRepository.findById(
            NotificationReferenceOwnerId(referenceType.trim().uppercase().take(40), referenceId)
        ).orElse(null) ?: run {
            log.warn("No notification owner registered for {} {}", referenceType, referenceId)
            return null
        }
        return enqueueForUser(owner.userId, templateCode, idempotencyKey, variables)
    }

    fun enqueueForUser(
        userId: UUID,
        templateCode: String,
        idempotencyKey: String,
        variables: Map<String, Any?>,
    ): EmailDelivery? {
        if (!enabled()) return null
        val contact = contactRepository.findById(userId).orElse(null)
        val email = contact?.email?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: run {
            log.debug("No transactional email address registered for user {}", userId)
            return null
        }
        val normalizedKey = idempotencyKey.trim().take(180)
        require(normalizedKey.isNotBlank()) { "Email idempotency key must not be blank" }

        val existing = deliveryRepository.findByIdempotencyKey(normalizedKey)
        if (existing != null) return existing

        val templateVariables = mapOf(
            "customer_name" to (contact.displayName?.takeIf(String::isNotBlank) ?: "Customer")
        ) + variables
        val delivery = EmailDelivery(
            idempotencyKey = normalizedKey,
            userId = userId,
            recipientEmail = email,
            recipientName = contact.displayName,
            templateCode = templateCode.trim().uppercase().take(80),
            variablesJson = objectMapper.writeValueAsString(templateVariables),
            status = "PENDING",
            nextAttemptAt = Instant.now(),
        )

        val reserved = try {
            deliveryRepository.saveAndFlush(delivery)
        } catch (error: DataIntegrityViolationException) {
            deliveryRepository.findByIdempotencyKey(normalizedKey) ?: throw error
        }
        if (reserved.status == "PENDING") attemptDelivery(reserved)
        return deliveryRepository.findById(reserved.emailDeliveryId).orElse(reserved)
    }

    @Scheduled(fixedDelayString = "\${notification.email.retry-interval-ms:60000}")
    @SchedulerLock(name = "notification_transactionalEmailRetry", lockAtMostFor = "PT55S", lockAtLeastFor = "PT1S")
    fun retryDueEmails() {
        if (!enabled()) return
        val batch = deliveryRepository.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            listOf("RETRY"),
            Instant.now(),
            PageRequest.of(0, 50),
        )
        batch.forEach(::attemptDelivery)
    }

    private fun attemptDelivery(delivery: EmailDelivery) {
        if (delivery.status == "SENT" || delivery.status == "UNKNOWN") return
        if (delivery.attemptCount >= maxAttempts()) {
            markFailed(delivery, "Maximum email delivery attempts exceeded")
            return
        }

        delivery.attemptCount += 1
        delivery.updatedAt = Instant.now()
        delivery.nextAttemptAt = null
        deliveryRepository.save(delivery)

        val variables = runCatching {
            objectMapper.readValue(delivery.variablesJson, variablesType)
        }.getOrElse { error ->
            markFailed(delivery, "Stored template variables are invalid: ${error.message}")
            return
        }
        val command = EmailProviderCommand(
            idempotencyKey = delivery.idempotencyKey,
            recipientEmail = delivery.recipientEmail,
            recipientName = delivery.recipientName,
            templateCode = delivery.templateCode,
            variables = variables,
        )

        val msg91Allowed = msg91Provider.isConfigured(delivery.templateCode) && msg91RemainingThisMonth()
        val primary = if (msg91Allowed) msg91Provider else brevoProvider
        if (!primary.isConfigured(delivery.templateCode)) {
            val alternate = if (primary.providerName == "MSG91") brevoProvider else msg91Provider
            if (alternate.isConfigured(delivery.templateCode) &&
                (alternate.providerName != "MSG91" || msg91RemainingThisMonth())) {
                handleResult(delivery, alternate.send(command), command, allowFailover = false)
            } else {
                markFailed(delivery, "No configured transactional email provider for ${delivery.templateCode}")
            }
            return
        }

        handleResult(delivery, primary.send(command), command, allowFailover = primary.providerName == "MSG91")
    }

    private fun handleResult(
        delivery: EmailDelivery,
        result: EmailProviderResult,
        command: EmailProviderCommand,
        allowFailover: Boolean,
    ) {
        if (result.accepted) {
            markSent(delivery, result)
            return
        }

        // A timeout/no-response is ambiguous: the provider may have accepted the request.
        // Do not immediately send via another provider because that can generate duplicate customer email.
        if (result.ambiguous) {
            delivery.provider = result.provider
            delivery.status = "UNKNOWN"
            delivery.lastError = result.error?.take(1000)
            delivery.updatedAt = Instant.now()
            deliveryRepository.save(delivery)
            log.error(
                "Transactional email outcome is ambiguous for {} via {}; manual/provider-log reconciliation required",
                delivery.idempotencyKey,
                result.provider,
            )
            return
        }

        if (allowFailover && result.safeToFailover && brevoProvider.isConfigured(delivery.templateCode)) {
            val backup = brevoProvider.send(command)
            if (backup.accepted) {
                markSent(delivery, backup)
                return
            }
            if (backup.ambiguous) {
                delivery.provider = backup.provider
                delivery.status = "UNKNOWN"
                delivery.lastError = backup.error?.take(1000)
                delivery.updatedAt = Instant.now()
                deliveryRepository.save(delivery)
                return
            }
            scheduleOrFail(delivery, backup)
            return
        }

        scheduleOrFail(delivery, result)
    }

    private fun scheduleOrFail(delivery: EmailDelivery, result: EmailProviderResult) {
        if (result.retryable && delivery.attemptCount < maxAttempts()) {
            val delaySeconds = minOf(3600L, 60L * (1L shl minOf(delivery.attemptCount, 5)))
            delivery.provider = result.provider
            delivery.status = "RETRY"
            delivery.nextAttemptAt = Instant.now().plusSeconds(delaySeconds)
            delivery.lastError = result.error?.take(1000)
            delivery.updatedAt = Instant.now()
            deliveryRepository.save(delivery)
        } else {
            markFailed(delivery, result.error ?: "Email provider rejected the request", result.provider)
        }
    }

    private fun markSent(delivery: EmailDelivery, result: EmailProviderResult) {
        val now = Instant.now()
        delivery.provider = result.provider
        delivery.providerMessageId = result.providerMessageId
        delivery.status = "SENT"
        delivery.sentAt = now
        delivery.updatedAt = now
        delivery.lastError = null
        delivery.nextAttemptAt = null
        deliveryRepository.save(delivery)
    }

    private fun markFailed(delivery: EmailDelivery, error: String, provider: String? = delivery.provider) {
        delivery.provider = provider
        delivery.status = "FAILED"
        delivery.lastError = error.take(1000)
        delivery.updatedAt = Instant.now()
        delivery.nextAttemptAt = null
        deliveryRepository.save(delivery)
        log.error("Transactional email {} failed: {}", delivery.idempotencyKey, error)
    }

    private fun msg91RemainingThisMonth(): Boolean {
        val startOfMonth = ZonedDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        val sent = deliveryRepository.countByProviderAndStatusAndSentAtGreaterThanEqual("MSG91", "SENT", startOfMonth)
        return sent < msg91MonthlyLimit()
    }

    private fun enabled(): Boolean =
        environment.getProperty("notification.email.enabled", Boolean::class.java, false)

    private fun msg91MonthlyLimit(): Long =
        environment.getProperty("notification.email.msg91.monthly-limit", Long::class.java, 5000L)

    private fun maxAttempts(): Int =
        environment.getProperty("notification.email.max-attempts", Int::class.java, 5).coerceIn(1, 10)
}
