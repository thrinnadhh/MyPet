package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.config.NotificationTemplateProperties
import com.pawsnearme.notificationservice.model.ReminderDeliveryStatus
import com.pawsnearme.notificationservice.model.ScheduledReminder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class ReminderDispatchWorkerTests {

    private val transactionService: ReminderTransactionService = mock()
    private val deliveryAdapter: NotificationDeliveryAdapter = mock()
    private val templateProperties: NotificationTemplateProperties = mock<NotificationTemplateProperties>().also {
        whenever(it.messageFor(any(), any())).thenReturn("Test reminder message")
    }
    private val worker = ReminderDispatchWorker(transactionService, deliveryAdapter, templateProperties)

    private fun makeReminder(templateCode: String = "APPOINTMENT_T24H") = ScheduledReminder(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        referenceType = "APPOINTMENT",
        referenceId = UUID.randomUUID(),
        fireAt = Instant.now().minusSeconds(60),
        fired = false,
        templateCode = templateCode
    ).also { assertNotNull(it.id) }

    private fun ScheduledReminder.persistedId(): UUID = requireNotNull(id)

    @Test
    fun `dispatchDueReminders - no due reminders - does not record attempts`() {
        whenever(transactionService.findDueReminders(any())).thenReturn(emptyList())

        worker.dispatchDueReminders()

        verify(transactionService, never()).markAttempted(any(), any(), any())
        verify(transactionService, never()).markDelivered(any(), any(), any(), any())
        verify(deliveryAdapter, never()).deliver(any())
    }

    @Test
    fun `dispatchDueReminders - two due reminders - marks both delivered`() {
        val r1 = makeReminder("APPOINTMENT_T24H")
        val r2 = makeReminder("APPOINTMENT_T1H")
        whenever(transactionService.findDueReminders(any())).thenReturn(listOf(r1, r2))
        whenever(deliveryAdapter.deliver(any())).thenReturn(NotificationDeliveryResult(delivered = true, provider = "TEST"))

        worker.dispatchDueReminders()

        verify(transactionService).markAttempted(eq(r1.persistedId()), eq(ReminderDeliveryStatus.ATTEMPTED), any())
        verify(transactionService).markAttempted(eq(r2.persistedId()), eq(ReminderDeliveryStatus.ATTEMPTED), any())
        verify(transactionService).markDelivered(eq(r1.persistedId()), eq(ReminderDeliveryStatus.DELIVERED), eq("TEST"), any())
        verify(transactionService).markDelivered(eq(r2.persistedId()), eq(ReminderDeliveryStatus.DELIVERED), eq("TEST"), any())
    }

    @Test
    fun `dispatchDueReminders - logged dev delivery uses logged status`() {
        val reminder = makeReminder()
        whenever(transactionService.findDueReminders(any())).thenReturn(listOf(reminder))
        whenever(deliveryAdapter.deliver(any())).thenReturn(NotificationDeliveryResult(delivered = true, provider = "LOGGED_DEV"))

        worker.dispatchDueReminders()

        verify(transactionService, times(1)).markDelivered(
            eq(reminder.persistedId()),
            eq(ReminderDeliveryStatus.DELIVERED_LOGGED),
            eq("LOGGED_DEV"),
            any()
        )
    }

    @Test
    fun `dispatchDueReminders - failed delivery is marked failed and not delivered`() {
        val reminder = makeReminder()
        whenever(transactionService.findDueReminders(any())).thenReturn(listOf(reminder))
        whenever(deliveryAdapter.deliver(any())).thenReturn(
            NotificationDeliveryResult(delivered = false, provider = "TEST", retryable = true, failureReason = "network")
        )

        worker.dispatchDueReminders()

        verify(transactionService).markAttempted(eq(reminder.persistedId()), eq(ReminderDeliveryStatus.ATTEMPTED), any())
        verify(transactionService).markFailed(
            eq(reminder.persistedId()),
            eq(ReminderDeliveryStatus.FAILED),
            eq("TEST"),
            eq(true),
            eq("network")
        )
        verify(transactionService, never()).markDelivered(eq(reminder.persistedId()), any(), any(), any())
    }
}
