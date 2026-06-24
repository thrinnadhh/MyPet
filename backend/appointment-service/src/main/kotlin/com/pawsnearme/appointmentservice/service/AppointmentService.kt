package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
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
    private val catalogServiceUrl: String
) {
    private val restTemplate = RestTemplate()

    fun bookAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = "lock:slots:${request.slotId}"
        
        // 1. Acquire Redis distributed lock with 5-minute TTL (300 seconds)
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(300))
        if (acquired != true) {
            throw IllegalStateException("Slot is currently being booked by another customer. Please try again.")
        }

        try {
            // 2. Check DB to ensure slot is not already booked/held
            val alreadyBooked = appointmentRepository.existsBySlotIdAndStatusNotIn(
                request.slotId, 
                listOf(AppointmentStatus.CANCELLED, AppointmentStatus.EXPIRED)
            )
            if (alreadyBooked) {
                throw IllegalStateException("Slot is already booked or held by another customer.")
            }

            // 3. Update slot status in Catalog Service to BOOKED
            val url = "$catalogServiceUrl/api/v1/catalog/slots/${request.slotId}/status?status=BOOKED"
            try {
                restTemplate.put(url, null)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to book slot in Catalog Service: ${e.message}", e)
            }

            // 4. Create and Save the Appointment
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

            // 5. Log history
            logStatusChange(saved.appointmentId!!, null, AppointmentStatus.CONFIRMED, saved.customerId, "Appointment booked and confirmed")

            // 6. Publish AppointmentBooked event to Kafka
            try {
                val event = AppointmentBookedEvent(
                    appointmentId = saved.appointmentId!!,
                    customerId = saved.customerId,
                    providerId = saved.providerId,
                    slotId = saved.slotId,
                    priceAmount = saved.priceAmount
                )
                kafkaTemplate.send("appointments.events", saved.appointmentId.toString(), event)
            } catch (e: Exception) {
                println("WARNING: Failed to publish Kafka AppointmentBooked event: ${e.message}")
            }

            return saved
        } finally {
            // 7. Always release the Redis lock
            redisTemplate.delete(lockKey)
        }
    }

    fun updateAppointmentStatus(appointmentId: UUID, newStatus: AppointmentStatus, changedBy: UUID, note: String? = null): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }

        val oldStatus = appointment.status
        appointment.status = newStatus

        when (newStatus) {
            AppointmentStatus.COMPLETED -> appointment.completedAt = Instant.now()
            AppointmentStatus.CANCELLED -> {
                appointment.cancelledAt = Instant.now()
                appointment.cancellationReason = note
                
                // Release the slot in Catalog Service (make it AVAILABLE again)
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

        // Publish status change to Kafka
        try {
            val event = mapOf(
                "appointmentId" to appointmentId.toString(),
                "fromStatus" to oldStatus.name,
                "toStatus" to newStatus.name,
                "timestamp" to Instant.now().toString()
            )
            kafkaTemplate.send("appointments.events", appointmentId.toString(), event)
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
