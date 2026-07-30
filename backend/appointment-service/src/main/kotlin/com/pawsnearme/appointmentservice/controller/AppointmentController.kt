package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentInvoice
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.service.BookAppointmentRequest
import com.pawsnearme.appointmentservice.service.AppointmentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController(
    private val appointmentService: AppointmentService,
    private val appointmentRepository: AppointmentRepository
) {

    @PostMapping
    fun bookAppointment(
        @Valid @RequestBody request: BookAppointmentRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val finalRequest = request.copy(customerId = UUID.fromString(authenticatedUserId))
        val appointment = appointmentService.bookAppointment(finalRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment)
    }

    @PostMapping("/hold")
    fun holdAppointment(
        @Valid @RequestBody request: BookAppointmentRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val finalRequest = request.copy(customerId = UUID.fromString(authenticatedUserId))
        val appointment = appointmentService.holdAppointment(finalRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment)
    }

    @PostMapping("/{id}/confirm")
    fun confirmAppointment(
        @PathVariable id: UUID,
        @RequestParam(required = false) paymentId: UUID?,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?,
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val appointment = appointmentService.confirmAppointment(
            id,
            paymentId,
            UUID.fromString(authenticatedUserId),
            authenticatedUserRole,
        )
        return ResponseEntity.ok(appointment)
    }

    @GetMapping("/{id}")
    fun getAppointment(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val appointment = appointmentService.getAppointment(id, callerId, authenticatedUserRole)
        return ResponseEntity.ok(appointment)
    }

    @GetMapping("/{id}/invoice")
    fun getAppointmentInvoice(@PathVariable id: UUID): ResponseEntity<AppointmentInvoice> {
        val invoice = appointmentService.getInvoiceByAppointmentId(id)
        return ResponseEntity.ok(invoice)
    }

    @GetMapping("/customer/{customerId}")
    fun getAppointmentsByCustomer(
        @PathVariable customerId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val appointments = appointmentService.getAppointmentsByCustomer(customerId, callerId, authenticatedUserRole)
        return ResponseEntity.ok(appointments)
    }

    @GetMapping("/provider/{providerId}")
    fun getAppointmentsByProvider(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val appointments = appointmentService.getAppointmentsByProvider(providerId, callerId, authenticatedUserRole)
        return ResponseEntity.ok(appointments)
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
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val changerId = UUID.fromString(authenticatedUserId)
        val updated = appointmentService.updateAppointmentStatus(id, status, changerId, note, prescriptionDocUrl, authenticatedUserRole)
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/{id}/reschedule")
    fun rescheduleAppointment(
        @PathVariable id: UUID,
        @RequestParam newSlotId: UUID,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?,
        @RequestHeader("X-User-Role", required = false) authenticatedUserRole: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        val callerId = UUID.fromString(authenticatedUserId)
        val rescheduled = appointmentService.rescheduleAppointment(id, newSlotId, callerId, authenticatedUserRole)
        return ResponseEntity.ok(rescheduled)
    }

}
