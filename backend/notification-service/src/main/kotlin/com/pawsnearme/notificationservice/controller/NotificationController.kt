package com.pawsnearme.notificationservice.controller

import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val reminderRepo: ScheduledReminderRepository
) {

    /** List all reminders for a given reference (appointment/order) id. */
    @GetMapping("/reminders/reference/{referenceId}")
    fun getByReference(@PathVariable referenceId: UUID): ResponseEntity<List<ScheduledReminder>> =
        ResponseEntity.ok(
            reminderRepo.findAll().filter { it.referenceId == referenceId }
        )

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
