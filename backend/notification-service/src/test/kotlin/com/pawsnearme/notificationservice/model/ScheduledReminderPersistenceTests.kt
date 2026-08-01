package com.pawsnearme.notificationservice.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ScheduledReminderPersistenceTests {
    @Test
    fun `new reminder leaves ID null so Spring Data uses persist`() {
        val reminder = reminder()

        assertNull(
            reminder.id,
            "A newly constructed reminder must not look like an existing detached entity"
        )
    }

    @Test
    fun `persisted reminder can carry its generated UUID`() {
        val reminder = reminder()
        val generatedId = UUID.randomUUID()

        reminder.id = generatedId

        assertEquals(generatedId, reminder.id)
        assertTrue(reminder.id != null)
    }

    private fun reminder() = ScheduledReminder(
        userId = UUID.randomUUID(),
        referenceType = "APPOINTMENT",
        referenceId = UUID.randomUUID(),
        fireAt = Instant.now().plusSeconds(3600),
        templateCode = "APPOINTMENT_T1H"
    )
}
