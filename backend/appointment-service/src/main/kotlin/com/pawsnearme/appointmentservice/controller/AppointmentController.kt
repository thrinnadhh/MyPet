package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.model.Appointment
import com.pawsnearme.appointmentservice.model.AppointmentInvoice
import com.pawsnearme.appointmentservice.model.AppointmentStatus
import com.pawsnearme.appointmentservice.repository.AppointmentRepository
import com.pawsnearme.appointmentservice.service.BookAppointmentRequest
import com.pawsnearme.appointmentservice.service.AppointmentService
import com.pawsnearme.appointmentservice.service.AppointmentAccessDeniedException
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
        return try {
            val finalRequest = request.copy(customerId = UUID.fromString(authenticatedUserId))
            val appointment = appointmentService.bookAppointment(finalRequest)
            ResponseEntity.status(HttpStatus.CREATED).body(appointment)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/hold")
    fun holdAppointment(
        @Valid @RequestBody request: BookAppointmentRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        if (authenticatedUserId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
        }
        return try {
            val finalRequest = request.copy(customerId = UUID.fromString(authenticatedUserId))
            val appointment = appointmentService.holdAppointment(finalRequest)
            ResponseEntity.status(HttpStatus.CREATED).body(appointment)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/confirm")
    fun confirmAppointment(
        @PathVariable id: UUID,
        @RequestParam(required = false) paymentId: UUID?
    ): ResponseEntity<Any> {
        return try {
            val appointment = appointmentService.confirmAppointment(id, paymentId)
            ResponseEntity.ok(appointment)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
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
        return try {
            val callerId = UUID.fromString(authenticatedUserId)
            val appointment = appointmentService.getAppointment(id, callerId, authenticatedUserRole)
            ResponseEntity.ok(appointment)
        } catch (e: AppointmentAccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}/invoice")
    fun getAppointmentInvoice(@PathVariable id: UUID): ResponseEntity<AppointmentInvoice> {
        return try {
            ResponseEntity.ok(appointmentService.getInvoiceByAppointmentId(id))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
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
        return try {
            val callerId = UUID.fromString(authenticatedUserId)
            val appointments = appointmentService.getAppointmentsByCustomer(customerId, callerId, authenticatedUserRole)
            ResponseEntity.ok(appointments)
        } catch (e: AppointmentAccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
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
        return try {
            val callerId = UUID.fromString(authenticatedUserId)
            val appointments = appointmentService.getAppointmentsByProvider(providerId, callerId, authenticatedUserRole)
            ResponseEntity.ok(appointments)
        } catch (e: AppointmentAccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
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
        return try {
            val changerId = UUID.fromString(authenticatedUserId)
            val updated = appointmentService.updateAppointmentStatus(id, status, changerId, authenticatedUserRole, note, prescriptionDocUrl)
            ResponseEntity.ok(updated)
        } catch (e: AppointmentAccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
