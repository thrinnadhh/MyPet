package com.pawsnearme.notificationservice.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ScheduledReminderPersistenceTests {
    @Test
    fun `assigned UUID reminder is new until persisted`() {
        val reminder = reminder()

        assertNotNull(reminder.id)
        assertTrue(reminder.isNew(), "Spring Data must call persist for a newly constructed reminder")

        reminder.markPersisted()

        assertFalse(reminder.isNew(), "Loaded or persisted reminders must use managed update semantics")
    }

    @Test
    fun `separate reminder instances remain independently new`() {
        val first = reminder()
        val second = reminder()

        first.markPersisted()

        assertFalse(first.isNew())
        assertTrue(second.isNew())
        assertTrue(first.id != second.id)
    }

    private fun reminder() = ScheduledReminder(
        userId = UUID.randomUUID(),
        referenceType = "APPOINTMENT",
        referenceId = UUID.randomUUID(),
        fireAt = Instant.now().plusSeconds(3600),
        templateCode = "APPOINTMENT_T1H"
    )
}
