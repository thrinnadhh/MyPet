package com.pawsnearme.appointmentservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonProperty
import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestOperations
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
    @get:JsonProperty("event_id")
    val eventId: UUID = UUID.randomUUID(),
    @get:JsonProperty("event_type")
    val eventType: String = "AppointmentBooked",
    @get:JsonProperty("occurred_at")
    val occurredAt: String = Instant.now().toString(),
    @get:JsonProperty("actor_id")
    val actorId: UUID,
    @get:JsonProperty("appointment_id")
    val appointmentId: UUID,
    @get:JsonProperty("customer_id")
    val customerId: UUID,
    @get:JsonProperty("provider_id")
    val providerId: UUID,
    @get:JsonProperty("slot_id")
    val slotId: UUID,
    @get:JsonProperty("slot_start")
    val slotStart: String? = null,
    @get:JsonProperty("price_amount")
    val priceAmount: BigDecimal
)

data class CatalogSlotSnapshot(
    val slotId: UUID? = null,
    val slotStart: Instant? = null,
    val slotEnd: Instant? = null,
    val status: String? = null
)

data class AppointmentStatusChangedEvent(
    @get:JsonProperty("event_id")
    val eventId: UUID = UUID.randomUUID(),
    @get:JsonProperty("event_type")
    val eventType: String = "AppointmentStatusChanged",
    @get:JsonProperty("occurred_at")
    val occurredAt: String = Instant.now().toString(),
    @get:JsonProperty("actor_id")
    val actorId: UUID,
    @get:JsonProperty("appointment_id")
    val appointmentId: UUID,
    @get:JsonProperty("slot_id")
    val slotId: UUID,
    @get:JsonProperty("from_status")
    val fromStatus: String,
    @get:JsonProperty("to_status")
    val toStatus: String
)

@Service
@Transactional
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val restTemplate: RestOperations,
    @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}")
    private val catalogServiceUrl: String,
    @Value("\${appointment.hold-duration-seconds:300}")
    private val holdDurationSeconds: Long
) {
    private val objectMapper = ObjectMapper()
    private val writeLockDuration = Duration.ofSeconds(10)
    private val holdDuration: Duration
        get() = Duration.ofSeconds(holdDurationSeconds)

    private fun writeLockKey(slotId: UUID) = "lock:slots:$slotId"

    private fun holdKey(slotId: UUID) = "hold:slots:$slotId"

    private fun acquireWriteLock(slotId: UUID): Boolean =
        redisTemplate.opsForValue().setIfAbsent(writeLockKey(slotId), "locked", writeLockDuration) == true

    private fun activeAppointmentExists(slotId: UUID): Boolean =
        appointmentRepository.existsBySlotIdAndStatusNotIn(
            slotId,
            listOf(AppointmentStatus.CANCELLED, AppointmentStatus.EXPIRED)
        )

    private fun updateCatalogSlotStatus(slotId: UUID, status: String) {
        val url = "$catalogServiceUrl/api/v1/catalog/slots/$slotId/status?status=$status"
        restTemplate.put(url, null)
    }

    private fun fetchCatalogSlotStart(slotId: UUID): String? {
        return try {
            val url = "$catalogServiceUrl/api/v1/catalog/slots/$slotId"
            restTemplate.getForObject(url, CatalogSlotSnapshot::class.java)?.slotStart?.toString()
        } catch (e: Exception) {
            println("WARNING: Failed to read slot start from Catalog Service: ${e.message}")
            null
        }
    }

    private fun publishAppointmentBooked(saved: Appointment) {
        try {
            val event = AppointmentBookedEvent(
                actorId = saved.customerId,
                appointmentId = saved.appointmentId!!,
                customerId = saved.customerId,
                providerId = saved.providerId,
                slotId = saved.slotId,
                slotStart = fetchCatalogSlotStart(saved.slotId),
                priceAmount = saved.priceAmount
            )
            kafkaTemplate.send("appointments.events", saved.appointmentId.toString(), objectMapper.writeValueAsString(event))
        } catch (e: Exception) {
            println("WARNING: Failed to publish Kafka AppointmentBooked event: ${e.message}")
        }
    }

    private fun publishAppointmentStatusChanged(
        appointment: Appointment,
        fromStatus: AppointmentStatus,
        toStatus: AppointmentStatus,
        actorId: UUID
    ) {
        try {
            val event = AppointmentStatusChangedEvent(
                actorId = actorId,
                appointmentId = appointment.appointmentId!!,
                slotId = appointment.slotId,
                fromStatus = fromStatus.name,
                toStatus = toStatus.name
            )
            kafkaTemplate.send("appointments.events", appointment.appointmentId.toString(), objectMapper.writeValueAsString(event))
        } catch (e: Exception) {
            println("WARNING: Failed to publish Kafka AppointmentStatusChanged event: ${e.message}")
        }
    }

    // --- Direct Booking (original single-stage method kept for backward compatibility) ---
    fun bookAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = writeLockKey(request.slotId)
        if (!acquireWriteLock(request.slotId)) {
            throw IllegalStateException("Slot is currently being booked by another customer. Please try again.")
        }

        try {
            if (activeAppointmentExists(request.slotId)) {
                throw IllegalStateException("Slot is already booked or held by another customer.")
            }

            try {
                updateCatalogSlotStatus(request.slotId, "BOOKED")
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
            publishAppointmentBooked(saved)

            return saved
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    // --- Two-stage Checkout: Hold Slot (Task 2) ---
    fun holdAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = writeLockKey(request.slotId)
        if (!acquireWriteLock(request.slotId)) {
            throw IllegalStateException("Slot is currently being booked by another customer. Please try again.")
        }

        try {
            if (activeAppointmentExists(request.slotId) || redisTemplate.hasKey(holdKey(request.slotId)) == true) {
                throw IllegalStateException("Slot is already booked or held by another customer.")
            }

            try {
                updateCatalogSlotStatus(request.slotId, "HELD")
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
            redisTemplate.opsForValue().set(holdKey(saved.slotId), saved.appointmentId.toString(), holdDuration)

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
        if (appointment.bookedAt.isBefore(cutoff) || redisTemplate.hasKey(holdKey(appointment.slotId)) == false) {
            appointment.status = AppointmentStatus.EXPIRED
            appointmentRepository.save(appointment)
            logStatusChange(appointmentId, AppointmentStatus.SLOT_HELD, AppointmentStatus.EXPIRED, appointment.customerId, "Hold expired before confirmation")
            redisTemplate.delete(holdKey(appointment.slotId))
            
            try {
                updateCatalogSlotStatus(appointment.slotId, "AVAILABLE")
            } catch (e: Exception) {
                println("WARNING: Failed to set slot back to AVAILABLE in Catalog Service: ${e.message}")
            }
            throw IllegalStateException("Slot hold has expired. Please select the slot and try again.")
        }

        try {
            updateCatalogSlotStatus(appointment.slotId, "BOOKED")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to book slot in Catalog Service: ${e.message}", e)
        }

        val oldStatus = appointment.status
        appointment.status = AppointmentStatus.CONFIRMED
        appointment.paymentId = paymentId
        val saved = appointmentRepository.save(appointment)

        logStatusChange(saved.appointmentId!!, oldStatus, AppointmentStatus.CONFIRMED, saved.customerId, "Appointment confirmed")
        redisTemplate.delete(holdKey(saved.slotId))
        publishAppointmentBooked(saved)
        publishAppointmentStatusChanged(saved, oldStatus, AppointmentStatus.CONFIRMED, saved.customerId)

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
                redisTemplate.delete(holdKey(appointment.slotId))

                updateCatalogSlotStatus(appointment.slotId, "AVAILABLE")
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
                
                try {
                    updateCatalogSlotStatus(appointment.slotId, "AVAILABLE")
                    redisTemplate.delete(holdKey(appointment.slotId))
                } catch (e: Exception) {
                    println("WARNING: Failed to set slot back to AVAILABLE in Catalog Service: ${e.message}")
                }
            }
            else -> {}
        }

        val updated = appointmentRepository.save(appointment)
        logStatusChange(appointmentId, oldStatus, newStatus, changedBy, note)
        publishAppointmentStatusChanged(updated, oldStatus, newStatus, changedBy)

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
