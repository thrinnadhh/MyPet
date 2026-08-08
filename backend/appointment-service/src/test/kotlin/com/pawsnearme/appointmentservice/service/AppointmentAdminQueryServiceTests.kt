package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.repository.AppointmentStatusHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class AppointmentAdminQueryServiceTests {
    private val appointmentRepository: AppointmentRepository = mock()
    private val historyRepository: AppointmentStatusHistoryRepository = mock()
    private val service = AppointmentAdminQueryService(appointmentRepository, historyRepository)

    @Test
    fun `admin appointment list is bounded`() {
        whenever(appointmentRepository.findAll(any<Pageable>()))
            .thenAnswer { invocation -> PageImpl<Appointment>(emptyList(), invocation.getArgument(0), 0L) }

        val result = service.list(0, 25)

        assertEquals(0L, result.totalElements)
        assertEquals(25, result.size)
    }

    @Test
    fun `admin appointment list rejects unbounded requests`() {
        assertThrows<IllegalArgumentException> { service.list(0, 1000) }
        verify(appointmentRepository, never()).findAll(any<Pageable>())
    }
}
