package com.pawsnearme.appointmentservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class BookAppointmentRequest(
    val customerId: UUID,
    val providerId: UUID,
    val offeringId: UUID,
    val slotId: UUID,
    val petId: UUID,
    val priceAmount: BigDecimal,
    val payAtClinic: Boolean = false
)

data class AppointmentBookedEvent(
    val appointmentId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val slotId: UUID,
    val priceAmount: BigDecimal,
    val timestamp: Instant = Instant.now()
)

@Service
@Transactional
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}")
    private val catalogServiceUrl: String,
    @Value("\${appointment.hold-duration-seconds:300}")
    private val holdDurationSeconds: Long
) {
    private val restTemplate = RestTemplate()

    // --- Direct Booking (original single-stage method kept for backward compatibility) ---
    fun bookAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = "lock:slots:${request.slotId}"
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(300))
        if (acquired != true) {
            throw IllegalStateException("Slot is currently being booked by another customer. Please try again.")
        }

        try {
            val alreadyBooked = appointmentRepository.existsBySlotIdAndStatusNotIn(
                request.slotId, 
                listOf(AppointmentStatus.CANCELLED, AppointmentStatus.EXPIRED)
            )
            if (alreadyBooked) {
                throw IllegalStateException("Slot is already booked or held by another customer.")
            }

            val url = "$catalogServiceUrl/api/v1/catalog/slots/${request.slotId}/status?status=BOOKED"
            try {
                restTemplate.put(url, null)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to book slot in Catalog Service: ${e.message}", e)
            }

            val appointment = Appointment(
                customerId = request.customerId,
                providerId = request.providerId,
                offeringId = request.offeringId,
                slotId = request.slotId,
                petId = request.petId,
                status = AppointmentStatus.CONFIRMED,
                priceAmount = request.priceAmount,
                payAtClinic = request.payAtClinic
            )
            val saved = appointmentRepository.save(appointment)

            logStatusChange(saved.appointmentId!!, null, AppointmentStatus.CONFIRMED, saved.customerId, "Appointment booked and confirmed")

            try {
                val event = AppointmentBookedEvent(
                    appointmentId = saved.appointmentId!!,
                    customerId = saved.customerId,
                    providerId = saved.providerId,
                    slotId = saved.slotId,
                    priceAmount = saved.priceAmount
                )
                val jsonString = ObjectMapper().writeValueAsString(event)
                kafkaTemplate.send("appointments.events", saved.appointmentId.toString(), jsonString)
            } catch (e: Exception) {
                println("WARNING: Failed to publish Kafka AppointmentBooked event: ${e.message}")
            }

            return saved
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    // --- Two-stage Checkout: Hold Slot (Task 2) ---
    fun holdAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = "lock:slots:${request.slotId}"
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(300))
        if (acquired != true) {
            throw IllegalStateException("Slot is currently being booked by another customer. Please try again.")
        }

        try {
            val alreadyBooked = appointmentRepository.existsBySlotIdAndStatusNotIn(
                request.slotId, 
                listOf(AppointmentStatus.CANCELLED, AppointmentStatus.EXPIRED)
            )
            if (alreadyBooked) {
                throw IllegalStateException("Slot is already booked or held by another customer.")
            }

            val url = "$catalogServiceUrl/api/v1/catalog/slots/${request.slotId}/status?status=HELD"
            try {
                restTemplate.put(url, null)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to hold slot in Catalog Service: ${e.message}", e)
            }

            val appointment = Appointment(
                customerId = request.customerId,
                providerId = request.providerId,
                offeringId = request.offeringId,
                slotId = request.slotId,
                petId = request.petId,
                status = AppointmentStatus.SLOT_HELD,
                priceAmount = request.priceAmount,
                payAtClinic = request.payAtClinic
            )
            val saved = appointmentRepository.save(appointment)

            logStatusChange(saved.appointmentId!!, null, AppointmentStatus.SLOT_HELD, saved.customerId, "Slot held for customer")

            return saved
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    // --- Two-stage Checkout: Confirm Slot (Task 2) ---
    fun confirmAppointment(appointmentId: UUID, paymentId: UUID? = null): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }

        if (appointment.status != AppointmentStatus.SLOT_HELD) {
            throw IllegalStateException("Appointment is not in SLOT_HELD state. Current state: ${appointment.status}")
        }

        val cutoff = Instant.now().minusSeconds(holdDurationSeconds)
        if (appointment.bookedAt.isBefore(cutoff)) {
            appointment.status = AppointmentStatus.EXPIRED
            appointmentRepository.save(appointment)
            logStatusChange(appointmentId, AppointmentStatus.SLOT_HELD, AppointmentStatus.EXPIRED, appointment.customerId, "Hold expired before confirmation")
            
            val url = "$catalogServiceUrl/api/v1/catalog/slots/${appointment.slotId}/status?status=AVAILABLE"
            try {
                restTemplate.put(url, null)
            } catch (e: Exception) {
                println("WARNING: Failed to set slot back to AVAILABLE in Catalog Service: ${e.message}")
            }
            throw IllegalStateException("Slot hold has expired. Please select the slot and try again.")
        }

        val url = "$catalogServiceUrl/api/v1/catalog/slots/${appointment.slotId}/status?status=BOOKED"
        try {
            restTemplate.put(url, null)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to book slot in Catalog Service: ${e.message}", e)
        }

        val oldStatus = appointment.status
        appointment.status = AppointmentStatus.CONFIRMED
        appointment.paymentId = paymentId
        val saved = appointmentRepository.save(appointment)

        logStatusChange(saved.appointmentId!!, oldStatus, AppointmentStatus.CONFIRMED, saved.customerId, "Appointment confirmed")

        try {
            val event = AppointmentBookedEvent(
                appointmentId = saved.appointmentId!!,
                customerId = saved.customerId,
                providerId = saved.providerId,
                slotId = saved.slotId,
                priceAmount = saved.priceAmount
            )
            val jsonString = ObjectMapper().writeValueAsString(event)
            kafkaTemplate.send("appointments.events", saved.appointmentId.toString(), jsonString)
        } catch (e: Exception) {
            println("WARNING: Failed to publish Kafka AppointmentBooked event: ${e.message}")
        }

        return saved
    }

    // --- Hold Expiry Cleanup Scheduler (Task 2) ---
    @Scheduled(fixedDelay = 5000)
    fun cleanupExpiredHolds() {
        val cutoff = Instant.now().minusSeconds(holdDurationSeconds)
        val expiredAppointments = appointmentRepository.findByStatusAndBookedAtBefore(AppointmentStatus.SLOT_HELD, cutoff)
        for (appointment in expiredAppointments) {
            try {
                appointment.status = AppointmentStatus.EXPIRED
                appointmentRepository.save(appointment)
                logStatusChange(appointment.appointmentId!!, AppointmentStatus.SLOT_HELD, AppointmentStatus.EXPIRED, appointment.customerId, "Slot hold expired")

                val url = "$catalogServiceUrl/api/v1/catalog/slots/${appointment.slotId}/status?status=AVAILABLE"
                restTemplate.put(url, null)
                println("AppointmentService: Slot hold expired for appointment ${appointment.appointmentId}, slot ${appointment.slotId} is now AVAILABLE.")
            } catch (e: Exception) {
                println("WARNING: Failed to expire slot hold for appointment ${appointment.appointmentId}: ${e.message}")
            }
        }
    }

    fun updateAppointmentStatus(appointmentId: UUID, newStatus: AppointmentStatus, changedBy: UUID, note: String? = null, prescriptionDocUrl: String? = null): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }

        val oldStatus = appointment.status
        appointment.status = newStatus

        when (newStatus) {
            AppointmentStatus.COMPLETED -> {
                appointment.completedAt = Instant.now()
                if (prescriptionDocUrl != null) {
                    appointment.prescriptionDocUrl = prescriptionDocUrl
                }
            }
            AppointmentStatus.CANCELLED -> {
                appointment.cancelledAt = Instant.now()
                appointment.cancellationReason = note
                
                val url = "$catalogServiceUrl/api/v1/catalog/slots/${appointment.slotId}/status?status=AVAILABLE"
                try {
                    restTemplate.put(url, null)
                } catch (e: Exception) {
                    println("WARNING: Failed to set slot back to AVAILABLE in Catalog Service: ${e.message}")
                }
            }
            else -> {}
        }

        val updated = appointmentRepository.save(appointment)
        logStatusChange(appointmentId, oldStatus, newStatus, changedBy, note)

        try {
            val event = mapOf(
                "appointmentId" to appointmentId.toString(),
                "fromStatus" to oldStatus.name,
                "toStatus" to newStatus.name,
                "timestamp" to Instant.now().toString()
            )
            val jsonString = ObjectMapper().writeValueAsString(event)
            kafkaTemplate.send("appointments.events", appointmentId.toString(), jsonString)
        } catch (e: Exception) {
            println("WARNING: Failed to publish Kafka AppointmentStatusChanged event: ${e.message}")
        }

        return updated
    }

    private fun logStatusChange(appointmentId: UUID, from: AppointmentStatus?, to: AppointmentStatus, by: UUID, note: String?) {
        val history = AppointmentStatusHistory(
            appointmentId = appointmentId,
            fromStatus = from,
            toStatus = to,
            changedByUserId = by,
            note = note
        )
        appointmentStatusHistoryRepository.save(history)
    }
}
