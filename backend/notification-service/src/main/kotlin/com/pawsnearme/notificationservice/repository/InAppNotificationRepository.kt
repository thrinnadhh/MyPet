package com.pawsnearme.notificationservice.repository

import com.pawsnearme.notificationservice.model.InAppNotification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InAppNotificationRepository : JpaRepository<InAppNotification, UUID> {
    fun findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId: UUID): List<InAppNotification>
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<InAppNotification>
}
