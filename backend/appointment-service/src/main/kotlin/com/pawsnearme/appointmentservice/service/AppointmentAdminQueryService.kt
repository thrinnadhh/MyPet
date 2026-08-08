package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentStatusHistory
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.repository.AppointmentStatusHistoryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class AdminAppointmentPage(
    val content: List<Appointment>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class AdminAppointmentDetail(
    val appointment: Appointment,
    val timeline: List<AppointmentStatusHistory>
)

@Service
class AppointmentAdminQueryService(
    private val appointmentRepository: AppointmentRepository,
    private val historyRepository: AppointmentStatusHistoryRepository
) {
    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): AdminAppointmentPage {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val result = appointmentRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        return AdminAppointmentPage(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun detail(appointmentId: UUID): AdminAppointmentDetail {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment not found: $appointmentId") }
        return AdminAppointmentDetail(
            appointment = appointment,
            timeline = historyRepository.findByAppointmentId(appointmentId).sortedBy { it.changedAt }
        )
    }
}
