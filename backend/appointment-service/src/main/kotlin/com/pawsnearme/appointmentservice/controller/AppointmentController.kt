package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.model.AppointmentInvoice
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.repository.AppointmentStatusHistoryRepository
import com.pawsnearme.appointmentservice.service.AppointmentLifecyclePolicy
import com.pawsnearme.appointmentservice.service.AppointmentService
import com.pawsnearme.appointmentservice.service.BookAppointmentRequest
import com.pawsnearme.appointmentservice.service.MerchantAppointmentQueryService
import com.pawsnearme.common.module.ProviderModuleApi
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController(
    private val appointmentService: AppointmentService,
    private val appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
    private val lifecyclePolicy: AppointmentLifecyclePolicy,
    private val merchantAppointmentQueryService: MerchantAppointmentQueryService,
    private val providerModule: ProviderModuleApi,
) {
    private fun parseAuthenticatedUserId(value: String?): UUID? =
        value?.takeIf(String::isNotBlank)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(mapOf("error" to "Missing or invalid authenticated user context."))

    private fun providerUnavailable(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(
            mapOf(
                "code" to "PROVIDER_NOT_OPERATIONAL",
                "error" to "This provider is not accepting new appointments."
            )
        )

    @PostMapping
    fun bookAppointment(
        @Valid @RequestBody request: BookAppointmentRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        if (!providerModule.providerOperational(request.providerId)) return providerUnavailable()
        val appointment = appointmentService.bookAppointment(request.copy(customerId = callerId))
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment)
    }

    @PostMapping("/hold")
    fun holdAppointment(
        @Valid @RequestBody request: BookAppointmentRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        if (!providerModule.providerOperational(request.providerId)) return providerUnavailable()
        val appointment = appointmentService.holdAppointment(request.copy(customerId = callerId))
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment)
    }

    @PostMapping("/{id}/confirm")
    fun confirmAppointment(
        @PathVariable id: UUID,
        @RequestParam(required = false) paymentId: UUID?,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        return ResponseEntity.ok(
            appointmentService.confirmAppointment(id, paymentId, callerId, authenticatedUserRole)
        )
    }

    @GetMapping("/{id}")
    fun getAppointment(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        return ResponseEntity.ok(appointmentService.getAppointment(id, callerId, authenticatedUserRole))
    }

    @GetMapping("/{id}/history")
    fun getAppointmentHistory(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        appointmentService.getAppointment(id, callerId, authenticatedUserRole)
        return ResponseEntity.ok(
            appointmentStatusHistoryRepository.findByAppointmentId(id).sortedBy { it.changedAt }
        )
    }

    @GetMapping("/{id}/invoice")
    fun getAppointmentInvoice(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        appointmentService.getAppointment(id, callerId, authenticatedUserRole)
        val invoice: AppointmentInvoice = appointmentService.getInvoiceByAppointmentId(id)
        return ResponseEntity.ok(invoice)
    }

    @GetMapping("/customer/{customerId}")
    fun getAppointmentsByCustomer(
        @PathVariable customerId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        return ResponseEntity.ok(
            appointmentService.getAppointmentsByCustomer(customerId, callerId, authenticatedUserRole)
        )
    }

    @GetMapping("/provider/{providerId}")
    fun getAppointmentsByProvider(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        return ResponseEntity.ok(
            merchantAppointmentQueryService.listProviderAppointments(
                providerId,
                callerId,
                authenticatedUserRole,
            )
        )
    }

    @PutMapping("/{id}/status")
    fun updateAppointmentStatus(
        @PathVariable id: UUID,
        @RequestParam status: AppointmentStatus,
        @RequestParam(required = false) note: String?,
        @RequestParam(required = false) prescriptionDocUrl: String?,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        val current = appointmentService.getAppointment(id, callerId, authenticatedUserRole)
        lifecyclePolicy.requireAllowed(
            current = current.status,
            target = status,
            callerRole = authenticatedUserRole,
            callerIsCustomer = current.customerId == callerId
        )
        return ResponseEntity.ok(
            appointmentService.updateAppointmentStatus(
                id,
                status,
                callerId,
                note,
                prescriptionDocUrl,
                authenticatedUserRole
            )
        )
    }

    @PostMapping("/{id}/reschedule")
    fun rescheduleAppointment(
        @PathVariable id: UUID,
        @RequestParam newSlotId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        val callerId = parseAuthenticatedUserId(authenticatedUserId) ?: return unauthorized()
        return ResponseEntity.ok(
            appointmentService.rescheduleAppointment(id, newSlotId, callerId, authenticatedUserRole)
        )
    }
}
