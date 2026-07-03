package com.pawsnearme.notificationservice.controller

import com.pawsnearme.notificationservice.model.InAppNotification
import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.model.DevicePushToken
import com.pawsnearme.notificationservice.repository.InAppNotificationRepository
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import com.pawsnearme.notificationservice.repository.DevicePushTokenRepository
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

data class RegisterPushTokenRequest(
    @field:NotBlank val expoPushToken: String,
    @field:NotBlank val platform: String,
    val appRole: String? = null,
    val soundProfile: String = "default",
)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val reminderRepo: ScheduledReminderRepository,
    private val inAppRepo: InAppNotificationRepository,
    private val pushTokenRepo: DevicePushTokenRepository,
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

    @PostMapping("/push-tokens")
    fun registerPushToken(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: RegisterPushTokenRequest,
    ): ResponseEntity<DevicePushToken> {
        val ownerId = UUID.fromString(userId)
        val existing = pushTokenRepo.findByExpoPushToken(request.expoPushToken)
        val saved = if (existing != null) {
            existing.userId = ownerId
            existing.platform = request.platform
            existing.appRole = request.appRole
            existing.soundProfile = request.soundProfile
            existing.updatedAt = Instant.now()
            pushTokenRepo.save(existing)
        } else {
            pushTokenRepo.save(
                DevicePushToken(
                    userId = ownerId,
                    expoPushToken = request.expoPushToken,
                    platform = request.platform,
                    appRole = request.appRole,
                    soundProfile = request.soundProfile,
                )
            )
        }
        return ResponseEntity.ok(saved)
    }

    @DeleteMapping("/push-tokens")
    fun unregisterPushToken(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam token: String,
    ): ResponseEntity<Void> {
        pushTokenRepo.deleteByUserIdAndExpoPushToken(UUID.fromString(userId), token)
        return ResponseEntity.noContent().build()
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
