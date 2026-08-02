package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.AppointmentStatus
import org.springframework.stereotype.Component

@Component
class AppointmentLifecyclePolicy {
    fun requireAllowed(
        current: AppointmentStatus,
        target: AppointmentStatus,
        callerRole: String?,
        callerIsCustomer: Boolean
    ) {
        if (current == target) return
        if (current in TERMINAL_STATUSES) {
            throw IllegalStateException("Appointment in status $current cannot transition to $target.")
        }

        val role = callerRole?.uppercase()
        val allowed = when {
            role == "ADMIN" -> adminTransitions(current)
            role == "MERCHANT" -> merchantTransitions(current)
            callerIsCustomer -> customerTransitions(current)
            else -> emptySet()
        }

        if (target !in allowed) {
            throw AppointmentAccessDeniedException(
                "Appointment transition from $current to $target is not allowed for ${role ?: "CUSTOMER"}."
            )
        }
    }

    private fun merchantTransitions(current: AppointmentStatus): Set<AppointmentStatus> = when (current) {
        AppointmentStatus.SLOT_HELD -> setOf(AppointmentStatus.CANCELLED)
        AppointmentStatus.CONFIRMED -> setOf(
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW,
            AppointmentStatus.CANCELLED
        )
        else -> emptySet()
    }

    private fun customerTransitions(current: AppointmentStatus): Set<AppointmentStatus> = when (current) {
        AppointmentStatus.SLOT_HELD,
        AppointmentStatus.CONFIRMED -> setOf(AppointmentStatus.CANCELLED)
        else -> emptySet()
    }

    private fun adminTransitions(current: AppointmentStatus): Set<AppointmentStatus> =
        merchantTransitions(current) + customerTransitions(current)

    companion object {
        private val TERMINAL_STATUSES = setOf(
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW,
            AppointmentStatus.EXPIRED
        )
    }
}
