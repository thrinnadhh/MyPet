package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class MerchantAppointmentView(
    val appointmentId: UUID,
    val customerId: UUID,
    val customerName: String?,
    val providerId: UUID,
    val offeringId: UUID,
    val offeringName: String?,
    val slotId: UUID,
    val slotStartsAt: Instant?,
    val slotEndsAt: Instant?,
    val petId: UUID,
    val petName: String?,
    val status: AppointmentStatus,
    val priceAmount: BigDecimal,
    val paymentId: UUID?,
    val payAtClinic: Boolean,
    val visitNotes: String?,
    val prescriptionDocUrl: String?,
    val bookedAt: Instant,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val cancellationReason: String?,
)

data class MerchantAppointmentPage(
    val providerId: UUID,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val content: List<MerchantAppointmentView>,
)

@Service
class MerchantAppointmentQueryService(
    private val appointmentRepository: AppointmentRepository,
    private val providerModule: ProviderModuleApi,
    private val catalogModule: CatalogModuleApi,
) {
    /** Compatibility list capped to the newest 100 appointments. */
    @Transactional(readOnly = true)
    fun listProviderAppointments(
        providerId: UUID,
        callerId: UUID,
        callerRole: String?,
    ): List<MerchantAppointmentView> = listProviderAppointmentsPage(
        providerId = providerId,
        callerId = callerId,
        callerRole = callerRole,
        page = 0,
        size = 100,
    ).content

    @Transactional(readOnly = true)
    fun listProviderAppointmentsPage(
        providerId: UUID,
        callerId: UUID,
        callerRole: String?,
        page: Int,
        size: Int,
    ): MerchantAppointmentPage {
        assertProviderAccess(providerId, callerId, callerRole)
        val boundedPage = page.coerceAtLeast(0)
        val boundedSize = size.coerceIn(1, 100)
        val appointments = appointmentRepository.findByProviderIdOrderByBookedAtDesc(
            providerId,
            PageRequest.of(boundedPage, boundedSize),
        )
        return MerchantAppointmentPage(
            providerId = providerId,
            page = boundedPage,
            size = boundedSize,
            totalElements = appointments.totalElements,
            totalPages = appointments.totalPages,
            hasNext = appointments.hasNext(),
            content = appointments.content.map(::toView),
        )
    }

    private fun assertProviderAccess(providerId: UUID, callerId: UUID, callerRole: String?) {
        val role = callerRole?.trim()?.uppercase()
        val allowed = role == "ADMIN" ||
            (role == "MERCHANT" && providerModule.ownerUserId(providerId) == callerId)
        if (!allowed) throw AppointmentAccessDeniedException("Access denied to provider appointments.")
    }

    private fun toView(appointment: Appointment): MerchantAppointmentView {
        val identity = providerModule.customerPetIdentity(appointment.customerId, appointment.petId)
        val offering = runCatching { catalogModule.offering(appointment.offeringId) }.getOrNull()
            ?.takeIf { it.providerId == appointment.providerId }
        val slot = runCatching { catalogModule.slot(appointment.slotId) }.getOrNull()

        return MerchantAppointmentView(
            appointmentId = requireNotNull(appointment.appointmentId),
            customerId = appointment.customerId,
            customerName = identity?.customerName,
            providerId = appointment.providerId,
            offeringId = appointment.offeringId,
            offeringName = offering?.name,
            slotId = appointment.slotId,
            slotStartsAt = slot?.slotStart,
            slotEndsAt = slot?.slotEnd,
            petId = appointment.petId,
            petName = identity?.petName,
            status = appointment.status,
            priceAmount = appointment.priceAmount,
            paymentId = appointment.paymentId,
            payAtClinic = appointment.payAtClinic,
            visitNotes = appointment.visitNotes,
            prescriptionDocUrl = appointment.prescriptionDocUrl,
            bookedAt = appointment.bookedAt,
            completedAt = appointment.completedAt,
            cancelledAt = appointment.cancelledAt,
            cancellationReason = appointment.cancellationReason,
        )
    }
}
