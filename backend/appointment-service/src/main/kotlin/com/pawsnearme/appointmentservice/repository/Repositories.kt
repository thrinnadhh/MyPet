package com.pawsnearme.appointmentservice.repository

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentInvoice
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.model.AppointmentStatusHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {
    fun findByCustomerId(customerId: UUID): List<Appointment>
    fun findByProviderId(providerId: UUID): List<Appointment>
    fun findByProviderIdOrderByBookedAtDesc(providerId: UUID, pageable: Pageable): Page<Appointment>
    fun existsBySlotIdAndStatusNotIn(slotId: UUID, statuses: Collection<AppointmentStatus>): Boolean
    fun findByStatusAndBookedAtBefore(status: AppointmentStatus, cutoff: Instant): List<Appointment>
}

@Repository
interface AppointmentStatusHistoryRepository : JpaRepository<AppointmentStatusHistory, UUID> {
    fun findByAppointmentId(appointmentId: UUID): List<AppointmentStatusHistory>
}

@Repository
interface AppointmentInvoiceRepository : JpaRepository<AppointmentInvoice, UUID> {
    fun findByAppointmentId(appointmentId: UUID): java.util.Optional<AppointmentInvoice>
}
