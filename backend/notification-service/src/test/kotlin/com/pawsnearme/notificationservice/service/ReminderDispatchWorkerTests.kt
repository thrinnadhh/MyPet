package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

class ReminderDispatchWorkerTests {

    private val reminderRepo: ScheduledReminderRepository = mock()
    private val deliveryAdapter: NotificationDeliveryAdapter = mock()
    private val worker = ReminderDispatchWorker(reminderRepo, deliveryAdapter)

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
    fun `dispatchDueReminders - no due reminders - does not record attempts`() {
        whenever(reminderRepo.findDueReminders(any())).thenReturn(emptyList())

        worker.dispatchDueReminders()

        verify(reminderRepo, never()).markAttempted(any(), any(), any())
        verify(reminderRepo, never()).markDelivered(any(), any(), any(), any())
        verify(deliveryAdapter, never()).deliver(any())
    }

    @Test
    fun `dispatchDueReminders - two due reminders - marks both delivered`() {
        val r1 = makeReminder("APPOINTMENT_T24H")
        val r2 = makeReminder("APPOINTMENT_T1H")
        whenever(reminderRepo.findDueReminders(any())).thenReturn(listOf(r1, r2))
        whenever(deliveryAdapter.deliver(any())).thenReturn(NotificationDeliveryResult(delivered = true, provider = "TEST"))

        worker.dispatchDueReminders()

        verify(reminderRepo).markAttempted(eq(r1.id), eq(ReminderDeliveryStatus.ATTEMPTED), any())
        verify(reminderRepo).markAttempted(eq(r2.id), eq(ReminderDeliveryStatus.ATTEMPTED), any())
        verify(reminderRepo).markDelivered(eq(r1.id), eq(ReminderDeliveryStatus.DELIVERED), eq("TEST"), any())
        verify(reminderRepo).markDelivered(eq(r2.id), eq(ReminderDeliveryStatus.DELIVERED), eq("TEST"), any())
    }

    @Test
    fun `dispatchDueReminders - logged dev delivery uses logged status`() {
        val reminder = makeReminder()
        whenever(reminderRepo.findDueReminders(any())).thenReturn(listOf(reminder))
        whenever(deliveryAdapter.deliver(any())).thenReturn(NotificationDeliveryResult(delivered = true, provider = "LOGGED_DEV"))

        worker.dispatchDueReminders()

        verify(reminderRepo, times(1)).markDelivered(
            eq(reminder.id),
            eq(ReminderDeliveryStatus.DELIVERED_LOGGED),
            eq("LOGGED_DEV"),
            any()
        )
    }

    @Test
    fun `dispatchDueReminders - failed delivery is marked failed and not delivered`() {
        val reminder = makeReminder()
        whenever(reminderRepo.findDueReminders(any())).thenReturn(listOf(reminder))
        whenever(deliveryAdapter.deliver(any())).thenReturn(
            NotificationDeliveryResult(delivered = false, provider = "TEST", retryable = true, failureReason = "network")
        )

        worker.dispatchDueReminders()

        verify(reminderRepo).markAttempted(eq(reminder.id), eq(ReminderDeliveryStatus.ATTEMPTED), any())
        verify(reminderRepo).markFailed(eq(reminder.id), eq(ReminderDeliveryStatus.FAILED), eq("TEST"), eq(true), eq("network"))
        verify(reminderRepo, never()).markDelivered(eq(reminder.id), any(), any(), any())
    }
}
