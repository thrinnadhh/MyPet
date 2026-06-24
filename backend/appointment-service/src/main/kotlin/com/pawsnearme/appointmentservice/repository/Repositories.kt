package com.pawsnearme.appointmentservice.repository

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentStatusHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {
    fun findByCustomerId(customerId: UUID): List<Appointment>
    fun findByProviderId(providerId: UUID): List<Appointment>
    fun existsBySlotIdAndStatusNotIn(slotId: UUID, statuses: Collection<com.pawsnearme.appointmentservice.model.AppointmentStatus>): Boolean
}

@Repository
interface AppointmentStatusHistoryRepository : JpaRepository<AppointmentStatusHistory, UUID> {
    fun findByAppointmentId(appointmentId: UUID): List<AppointmentStatusHistory>
}
