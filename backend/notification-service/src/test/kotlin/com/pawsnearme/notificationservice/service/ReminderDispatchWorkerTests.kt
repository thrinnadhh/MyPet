package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

class ReminderDispatchWorkerTests {

    private val reminderRepo: ScheduledReminderRepository = mock()
    private val worker = ReminderDispatchWorker(reminderRepo)

    private fun makeReminder(templateCode: String = "APPOINTMENT_T24H") = ScheduledReminder(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        referenceType = "APPOINTMENT",
        referenceId = UUID.randomUUID(),
        fireAt = Instant.now().minusSeconds(60),
        fired = false,
        templateCode = templateCode
    )

    // ── dispatchDueReminders ──────────────────────────────────────────────────

    @Test
    fun `dispatchDueReminders - no due reminders - does not call markFired`() {
        whenever(reminderRepo.findDueReminders(any())).thenReturn(emptyList())

        worker.dispatchDueReminders()

        verify(reminderRepo, never()).markFired(any())
    }

    @Test
    fun `dispatchDueReminders - two due reminders - marks both fired`() {
        val r1 = makeReminder("APPOINTMENT_T24H")
        val r2 = makeReminder("APPOINTMENT_T1H")
        whenever(reminderRepo.findDueReminders(any())).thenReturn(listOf(r1, r2))

        worker.dispatchDueReminders()

        verify(reminderRepo).markFired(r1.id)
        verify(reminderRepo).markFired(r2.id)
    }

    @Test
    fun `dispatchDueReminders - each reminder marked fired exactly once`() {
        val reminder = makeReminder()
        whenever(reminderRepo.findDueReminders(any())).thenReturn(listOf(reminder))

        worker.dispatchDueReminders()

        verify(reminderRepo, times(1)).markFired(reminder.id)
    }
}
