package com.pawsnearme.common.idempotency

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
}
