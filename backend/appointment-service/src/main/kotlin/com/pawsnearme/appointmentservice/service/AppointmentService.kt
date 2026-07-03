package com.pawsnearme.appointmentservice.service

import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonProperty
import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import com.pawsnearme.common.outbox.OutboxService

class AppointmentAccessDeniedException(message: String) : RuntimeException(message)

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
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
    private val appointmentInvoiceRepository: AppointmentInvoiceRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val restTemplate: RestOperations,
    private val outboxService: OutboxService,
    @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}")
    private val catalogServiceUrl: String,
    @Value("\${appointment.hold-duration-seconds:300}")
    private val holdDurationSeconds: Long,
    @Value("\${PROVIDER_SERVICE_URL:http://localhost:8081}")
    private val providerServiceUrl: String = "http://localhost:8081",
    @Value("\${gateway.trust.secret:}")
    private val gatewayTrustSecret: String = ""
) {
    private val logger = LoggerFactory.getLogger(AppointmentService::class.java)
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
        restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.PUT,
            org.springframework.http.HttpEntity<Any>(internalHeaders()),
            Void::class.java
        )
    }

    private fun fetchCatalogSlotStart(slotId: UUID): String? {
        return try {
            val url = "$catalogServiceUrl/api/v1/catalog/slots/$slotId"
            restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                org.springframework.http.HttpEntity<Any>(internalHeaders()),
                CatalogSlotSnapshot::class.java
            ).body?.slotStart?.toString()
        } catch (e: Exception) {
            logger.warn("Failed to read slot start from Catalog Service: {}", e.message, e)
            null
        }
    }

    private fun publishAppointmentBooked(saved: Appointment) {
        val eventId = UUID.randomUUID()
        val event = AppointmentBookedEvent(
            eventId = eventId,
            actorId = saved.customerId,
            appointmentId = saved.appointmentId!!,
            customerId = saved.customerId,
            providerId = saved.providerId,
            slotId = saved.slotId,
            slotStart = fetchCatalogSlotStart(saved.slotId),
            priceAmount = saved.priceAmount
        )
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "APPOINTMENT",
            aggregateId = saved.appointmentId!!,
            eventType = "AppointmentBooked",
            eventPayload = event
        )
    }

    private fun publishAppointmentStatusChanged(
        appointment: Appointment,
        fromStatus: AppointmentStatus,
        toStatus: AppointmentStatus,
        actorId: UUID
    ) {
        val eventId = UUID.randomUUID()
        val event = AppointmentStatusChangedEvent(
            eventId = eventId,
            actorId = actorId,
            appointmentId = appointment.appointmentId!!,
            slotId = appointment.slotId,
            fromStatus = fromStatus.name,
            toStatus = toStatus.name
        )
        outboxService.saveEvent(
            eventId = eventId,
            aggregateType = "APPOINTMENT",
            aggregateId = appointment.appointmentId!!,
            eventType = "AppointmentStatusChanged",
            eventPayload = event
        )
    }

    fun bookAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = writeLockKey(request.slotId)
        if (!acquireWriteLock(request.slotId)) {
            throw IllegalStateException("Failed to acquire slot lock. Slot is currently being booked by another customer. Try again.")
        }
        try {
            if (activeAppointmentExists(request.slotId)) {
                throw IllegalStateException("Slot is already booked or held.")
            }

            try {
                updateCatalogSlotStatus(request.slotId, "BOOKED")
            } catch (e: Exception) {
                throw IllegalStateException("Failed to update slot status in Catalog Service: ${e.message}", e)
            }

            val appointment = Appointment(
                customerId = request.customerId,
                providerId = request.providerId,
                offeringId = request.offeringId,
                slotId = request.slotId,
                petId = request.petId,
                priceAmount = request.priceAmount,
                status = AppointmentStatus.CONFIRMED
            )
            val saved = try {
                appointmentRepository.save(appointment)
            } catch (e: Exception) {
                try {
                    updateCatalogSlotStatus(request.slotId, "AVAILABLE")
                } catch (rollbackEx: Exception) {
                    logger.error("Failed to revert slot status to AVAILABLE after database failure: {}", rollbackEx.message, rollbackEx)
                }
                throw e
            }

            try {
                logStatusChange(saved.appointmentId!!, null, AppointmentStatus.CONFIRMED, saved.customerId, "Direct appointment booking")
                publishAppointmentBooked(saved)
            } catch (e: Exception) {
                try {
                    updateCatalogSlotStatus(request.slotId, "AVAILABLE")
                } catch (rollbackEx: Exception) {
                    logger.error("Failed to revert slot status to AVAILABLE after booking event logging failure: {}", rollbackEx.message, rollbackEx)
                }
                throw e
            }

            return saved
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    fun holdAppointment(request: BookAppointmentRequest): Appointment {
        val lockKey = writeLockKey(request.slotId)
        if (!acquireWriteLock(request.slotId)) {
            throw IllegalStateException("Failed to acquire slot lock. Slot is currently being booked by another customer. Try again.")
        }
        try {
            if (activeAppointmentExists(request.slotId)) {
                throw IllegalStateException("Slot is already booked or held.")
            }

            try {
                updateCatalogSlotStatus(request.slotId, "HELD")
            } catch (e: Exception) {
                throw IllegalStateException("Failed to update slot status in Catalog Service: ${e.message}", e)
            }

            val appointment = Appointment(
                customerId = request.customerId,
                providerId = request.providerId,
                offeringId = request.offeringId,
                slotId = request.slotId,
                petId = request.petId,
                priceAmount = request.priceAmount,
                status = AppointmentStatus.SLOT_HELD
            )
            val saved = try {
                appointmentRepository.save(appointment)
            } catch (e: Exception) {
                try {
                    updateCatalogSlotStatus(request.slotId, "AVAILABLE")
                } catch (rollbackEx: Exception) {
                    logger.error("Failed to revert slot status to AVAILABLE after database failure: {}", rollbackEx.message, rollbackEx)
                }
                throw e
            }

            try {
                logStatusChange(saved.appointmentId!!, null, AppointmentStatus.SLOT_HELD, saved.customerId, "Slot held for customer")
                redisTemplate.opsForValue().set(holdKey(saved.slotId), saved.appointmentId.toString(), holdDuration)
            } catch (e: Exception) {
                try {
                    updateCatalogSlotStatus(request.slotId, "AVAILABLE")
                } catch (rollbackEx: Exception) {
                    logger.error("Failed to revert slot status to AVAILABLE after status logging failure: {}", rollbackEx.message, rollbackEx)
                }
                throw e
            }

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
                logger.warn("Failed to set slot back to AVAILABLE in Catalog Service: {}", e.message, e)
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
                logger.info("Slot hold expired for appointment {}, slot {} is now AVAILABLE.", appointment.appointmentId, appointment.slotId)
            } catch (e: Exception) {
                logger.error("Failed to expire slot hold for appointment {}: {}", appointment.appointmentId, e.message, e)
            }
        }
    }

    fun fetchProviderOwnerUserId(providerId: UUID): UUID? {
        return try {
            val url = "$providerServiceUrl/api/v1/providers/$providerId"
            val response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                org.springframework.http.HttpEntity<Any>(internalHeaders()),
                Map::class.java
            ).body
            val ownerIdStr = response?.get("ownerUserId") as? String
            ownerIdStr?.let { UUID.fromString(it) }
        } catch (e: Exception) {
            logger.warn("Failed to fetch provider owner from Provider Service: {}", e.message, e)
            null
        }
    }

    fun getAppointment(appointmentId: UUID, callerId: UUID, callerRole: String?): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }

        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isCustomer = appointment.customerId == callerId
        val isProviderStaff = if (callerRole?.uppercase() == "MERCHANT") {
            val ownerId = fetchProviderOwnerUserId(appointment.providerId)
            ownerId == callerId
        } else {
            false
        }

        if (!isAdmin && !isCustomer && !isProviderStaff) {
            throw AppointmentAccessDeniedException("Access denied to appointment data.")
        }
        return appointment
    }

    fun getAppointmentsByCustomer(targetCustomerId: UUID, callerId: UUID, callerRole: String?): List<Appointment> {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isCustomer = targetCustomerId == callerId

        if (!isAdmin && !isCustomer) {
            throw AppointmentAccessDeniedException("Access denied to customer appointment history.")
        }
        return appointmentRepository.findByCustomerId(targetCustomerId)
    }

    fun getAppointmentsByProvider(providerId: UUID, callerId: UUID, callerRole: String?): List<Appointment> {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isProviderStaff = if (callerRole?.uppercase() == "MERCHANT") {
            val ownerId = fetchProviderOwnerUserId(providerId)
            ownerId == callerId
        } else {
            false
        }

        if (!isAdmin && !isProviderStaff) {
            throw AppointmentAccessDeniedException("Access denied to provider appointments.")
        }
        return appointmentRepository.findByProviderId(providerId)
    }

    fun updateAppointmentStatus(
        appointmentId: UUID,
        newStatus: AppointmentStatus,
        changedBy: UUID,
        note: String? = null,
        prescriptionDocUrl: String? = null,
        callerRole: String? = "ADMIN"
    ): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }

        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isProviderStaff = if (callerRole?.uppercase() == "MERCHANT") {
            val ownerId = fetchProviderOwnerUserId(appointment.providerId)
            ownerId == changedBy
        } else {
            false
        }

        if (!isAdmin && !isProviderStaff) {
            throw AppointmentAccessDeniedException("Access denied to change appointment status.")
        }

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
                    logger.warn("Failed to set slot back to AVAILABLE in Catalog Service: {}", e.message, e)
                }
            }
            else -> {}
        }

        val updated = appointmentRepository.save(appointment)
        logStatusChange(appointmentId, oldStatus, newStatus, changedBy, note)
        if (newStatus == AppointmentStatus.COMPLETED) {
            generateInvoiceForAppointment(updated)
        }
        publishAppointmentStatusChanged(updated, oldStatus, newStatus, changedBy)

        return updated
    }

    fun getInvoiceByAppointmentId(appointmentId: UUID): AppointmentInvoice {
        return appointmentInvoiceRepository.findByAppointmentId(appointmentId)
            .orElseThrow { NoSuchElementException("Invoice not found for appointment $appointmentId") }
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

    private fun internalHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        if (gatewayTrustSecret.isNotBlank()) {
            headers.set("X-Internal-Gateway-Secret", gatewayTrustSecret)
        }
        return headers
    }

    private fun generateInvoiceForAppointment(appointment: Appointment) {
        val appointmentId = appointment.appointmentId ?: return
        if (appointmentInvoiceRepository.findByAppointmentId(appointmentId).isPresent) {
            return
        }

        val subtotal = appointment.priceAmount.setScale(2, RoundingMode.HALF_UP)
        val tax = subtotal.multiply(BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP)
        val total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP)
        val invoiceNumber = "APT-INV-${LocalDate.now().year}-${appointmentId.toString().take(8).uppercase()}"

        appointmentInvoiceRepository.save(
            AppointmentInvoice(
                appointmentId = appointmentId,
                invoiceNumber = invoiceNumber,
                subtotalAmount = subtotal,
                taxAmount = tax,
                totalAmount = total
            )
        )
    }
}
