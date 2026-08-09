package com.pawsnearme.notificationservice.repository

import com.pawsnearme.notificationservice.model.EmailDelivery
import com.pawsnearme.notificationservice.model.NotificationAdminAudit
import com.pawsnearme.notificationservice.model.NotificationContact
import com.pawsnearme.notificationservice.model.NotificationReferenceOwner
import com.pawsnearme.notificationservice.model.NotificationReferenceOwnerId
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.time.Instant
import java.util.UUID

interface NotificationContactRepository : JpaRepository<NotificationContact, UUID>

interface NotificationReferenceOwnerRepository : JpaRepository<NotificationReferenceOwner, NotificationReferenceOwnerId>

interface EmailDeliveryRepository : JpaRepository<EmailDelivery, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): EmailDelivery?

    fun findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        statuses: Collection<String>,
        now: Instant,
        pageable: Pageable,
    ): List<EmailDelivery>

    fun countByProviderAndStatusAndSentAtGreaterThanEqual(
        provider: String,
        status: String,
        sentAt: Instant,
    ): Long

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<EmailDelivery>
    fun findByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): Page<EmailDelivery>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByEmailDeliveryId(emailDeliveryId: UUID): EmailDelivery?
}

interface NotificationAdminAuditRepository : JpaRepository<NotificationAdminAudit, UUID> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<NotificationAdminAudit>
}
