package com.pawsnearme.appointmentservice.controller

import com.pawsnearme.appointmentservice.model.Appointment
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
        return try {
            // Validate that the customerId matches the authenticated user ID (or fallback in dev)
            val finalRequest = if (authenticatedUserId != null) {
                request.copy(customerId = UUID.fromString(authenticatedUserId))
            } else {
                request
            }
            val appointment = appointmentService.bookAppointment(finalRequest)
            ResponseEntity.status(HttpStatus.CREATED).body(appointment)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}")
    fun getAppointment(@PathVariable id: UUID): ResponseEntity<Appointment> {
        val appointment = appointmentRepository.findById(id)
        return if (appointment.isPresent) {
            ResponseEntity.ok(appointment.get())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/customer/{customerId}")
    fun getAppointmentsByCustomer(@PathVariable customerId: UUID): ResponseEntity<List<Appointment>> {
        val appointments = appointmentRepository.findByCustomerId(customerId)
        return ResponseEntity.ok(appointments)
    }

    @GetMapping("/provider/{providerId}")
    fun getAppointmentsByProvider(@PathVariable providerId: UUID): ResponseEntity<List<Appointment>> {
        val appointments = appointmentRepository.findByProviderId(providerId)
        return ResponseEntity.ok(appointments)
    }

    @PutMapping("/{id}/status")
    fun updateAppointmentStatus(
        @PathVariable id: UUID,
        @RequestParam status: AppointmentStatus,
        @RequestParam(required = false) note: String?,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Any> {
        return try {
            val changerId = if (authenticatedUserId != null) {
                UUID.fromString(authenticatedUserId)
            } else {
                UUID.randomUUID() // fallback if no auth header
            }
            val updated = appointmentService.updateAppointmentStatus(id, status, changerId, note)
            ResponseEntity.ok(updated)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
