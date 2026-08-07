package com.pawsnearme.common.idempotency

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.dao.DataAccessResourceFailureException
import java.util.UUID

class IdempotencyServiceTests {
    private val repository: ProcessedEventRepository = mock()
    private val service = IdempotencyService(repository)

    @Test
    fun `new event returns true`() {
        val eventId = UUID.randomUUID()
        whenever(repository.insertIfAbsent(eventId)).thenReturn(1)

        assertTrue(service.checkAndRecord(eventId))
    }

    @Test
    fun `duplicate event returns false`() {
        val eventId = UUID.randomUUID()
        whenever(repository.insertIfAbsent(eventId)).thenReturn(0)

        assertFalse(service.checkAndRecord(eventId))
    }

    @Test
    fun `database failure propagates instead of becoming duplicate`() {
        val eventId = UUID.randomUUID()
        whenever(repository.insertIfAbsent(eventId)).thenThrow(
            DataAccessResourceFailureException("database unavailable")
        )

        assertThrows(DataAccessResourceFailureException::class.java) {
            service.checkAndRecord(eventId)
        }
    }

    @Test
    fun `same transport event can be claimed independently by consumer scope`() {
        val eventId = UUID.randomUUID()
        whenever(repository.insertIfAbsent(any())).thenReturn(1)

        assertTrue(service.checkAndRecord("notification-orders", eventId))
        assertTrue(service.checkAndRecord("dispatch-orders", eventId))

        val captor = argumentCaptor<UUID>()
        verify(repository, times(2)).insertIfAbsent(captor.capture())
        assertNotEquals(captor.allValues[0], captor.allValues[1])
    }

    @Test
    fun `same scoped transport event derives a stable claim key`() {
        val eventId = UUID.randomUUID()
        whenever(repository.insertIfAbsent(any())).thenReturn(1, 0)

        assertTrue(service.checkAndRecord("dispatch-orders", eventId))
        assertFalse(service.checkAndRecord("dispatch-orders", eventId))

        val captor = argumentCaptor<UUID>()
        verify(repository, times(2)).insertIfAbsent(captor.capture())
        assertEquals(captor.allValues[0], captor.allValues[1])
    }
}
