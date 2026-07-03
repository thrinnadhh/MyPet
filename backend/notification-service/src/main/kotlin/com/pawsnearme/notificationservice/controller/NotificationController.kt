package com.pawsnearme.notificationservice.controller

import com.pawsnearme.notificationservice.model.InAppNotification
import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.InAppNotificationRepository
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val reminderRepo: ScheduledReminderRepository,
    private val inAppRepo: InAppNotificationRepository,
) {

    /** List all reminders for a given reference (appointment/order) id. */
    @GetMapping("/reminders/reference/{referenceId}")
    fun getByReference(@PathVariable referenceId: UUID): ResponseEntity<List<ScheduledReminder>> =
        ResponseEntity.ok(
            reminderRepo.findAll().filter { it.referenceId == referenceId }
        )

    @GetMapping("/in-app/me")
    fun myNotifications(@RequestHeader("X-User-Id") userId: String): ResponseEntity<List<InAppNotification>> =
        ResponseEntity.ok(inAppRepo.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId)))

    @GetMapping("/in-app/me/unread")
    fun myUnread(@RequestHeader("X-User-Id") userId: String): ResponseEntity<List<InAppNotification>> =
        ResponseEntity.ok(inAppRepo.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID.fromString(userId)))

    @PatchMapping("/in-app/{notificationId}/read")
    fun markRead(
        @RequestHeader("X-User-Id") userId: String,
        @PathVariable notificationId: UUID,
    ): ResponseEntity<InAppNotification> {
        val notification = inAppRepo.findById(notificationId).orElseThrow()
        if (notification.userId.toString() != userId) throw IllegalAccessException("Forbidden")
        notification.readAt = java.time.Instant.now()
        return ResponseEntity.ok(inAppRepo.save(notification))
    }

    /** Health check / status overview. */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val all     = reminderRepo.findAll()
        val pending = all.count { !it.fired }
        val fired   = all.count { it.fired }
        val byStatus = all.groupingBy { it.deliveryStatus.name }.eachCount()
        return ResponseEntity.ok(mapOf("pending" to pending, "fired" to fired, "deliveryStatus" to byStatus, "status" to "UP"))
    }
}
