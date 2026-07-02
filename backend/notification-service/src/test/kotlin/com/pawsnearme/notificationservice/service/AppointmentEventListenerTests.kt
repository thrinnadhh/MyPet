package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class AppointmentEventListenerTests {

    private val reminderRepo: ScheduledReminderRepository = mock()
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
    private val listener = AppointmentEventListener(reminderRepo, objectMapper)

    @Test
    fun `AppointmentBooked snake case event schedules appointment reminders`() {
        val appointmentId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val slotStart = Instant.now().plusSeconds(48 * 3600)
        whenever(reminderRepo.existsByReferenceIdAndTemplateCode(eq(appointmentId), any())).thenReturn(false)
        whenever(reminderRepo.save(any())).thenAnswer { invocation -> invocation.getArgument<ScheduledReminder>(0) }

        listener.onAppointmentEvent(
            """
            {
              "event_id": "${UUID.randomUUID()}",
              "event_type": "AppointmentBooked",
              "occurred_at": "${Instant.now()}",
              "actor_id": "$customerId",
              "appointment_id": "$appointmentId",
              "customer_id": "$customerId",
              "provider_id": "${UUID.randomUUID()}",
              "slot_id": "$slotId",
              "slot_start": "$slotStart",
              "price_amount": 500.00
            }
            """.trimIndent()
        )

        val reminderCaptor = argumentCaptor<ScheduledReminder>()
        verify(reminderRepo, times(2)).save(reminderCaptor.capture())

        val templateCodes = reminderCaptor.allValues.map { it.templateCode }.toSet()
        assertEquals(setOf("APPOINTMENT_T24H", "APPOINTMENT_T1H"), templateCodes)
        assertEquals(setOf(appointmentId), reminderCaptor.allValues.map { it.referenceId }.toSet())
        assertEquals(setOf(customerId), reminderCaptor.allValues.map { it.userId }.toSet())
    }
}
