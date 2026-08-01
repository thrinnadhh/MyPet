package com.pawsnearme.appointmentservice.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.pawsnearme.appointmentservice.model.*
import com.pawsnearme.appointmentservice.module.RemoteCatalogModuleApi
import com.pawsnearme.appointmentservice.module.RemotePaymentModuleApi
import com.pawsnearme.appointmentservice.module.RemoteProviderModuleApi
import com.pawsnearme.appointmentservice.repository.*
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.outbox.OutboxService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
    @get:JsonProperty("event_id") val eventId: UUID = UUID.randomUUID(),
    @get:JsonProperty("event_type") val eventType: String = "AppointmentBooked",
    @get:JsonProperty("occurred_at") val occurredAt: String = Instant.now().toString(),
    @get:JsonProperty("actor_id") val actorId: UUID,
    @get:JsonProperty("appointment_id") val appointmentId: UUID,
    @get:JsonProperty("customer_id") val customerId: UUID,
    @get:JsonProperty("provider_id") val providerId: UUID,
    @get:JsonProperty("slot_id") val slotId: UUID,
    @get:JsonProperty("slot_start") val slotStart: String? = null,
    @get:JsonProperty("price_amount") val priceAmount: BigDecimal
)

data class AppointmentStatusChangedEvent(
    @get:JsonProperty("event_id") val eventId: UUID = UUID.randomUUID(),
    @get:JsonProperty("event_type") val eventType: String = "AppointmentStatusChanged",
    @get:JsonProperty("occurred_at") val occurredAt: String = Instant.now().toString(),
    @get:JsonProperty("actor_id") val actorId: UUID,
    @get:JsonProperty("appointment_id") val appointmentId: UUID,
    @get:JsonProperty("slot_id") val slotId: UUID,
    @get:JsonProperty("from_status") val fromStatus: String,
    @get:JsonProperty("to_status") val toStatus: String
)

@Service
class AppointmentService @Autowired constructor(
    private val appointmentRepository: AppointmentRepository,
    private val appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
    private val appointmentInvoiceRepository: AppointmentInvoiceRepository,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val outboxService: OutboxService,
    private val catalogModule: CatalogModuleApi,
    private val providerModule: ProviderModuleApi,
    private val paymentModule: PaymentModuleApi,
    private val holdDurationSeconds: Long = 300L
) {
    constructor(
        appointmentRepository: AppointmentRepository,
        appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
        appointmentInvoiceRepository: AppointmentInvoiceRepository,
        redisTemplate: StringRedisTemplate,
        kafkaTemplate: KafkaTemplate<String, Any>,
        restTemplate: RestOperations,
        outboxService: OutboxService,
        catalogServiceUrl: String,
        holdDurationSeconds: Long,
        providerServiceUrl: String = "http://localhost:8081",
        paymentServiceUrl: String = "http://localhost:8090",
        gatewayTrustSecret: String = ""
    ) : this(
        appointmentRepository,
        appointmentStatusHistoryRepository,
        appointmentInvoiceRepository,
        redisTemplate,
        kafkaTemplate,
        outboxService,
        RemoteCatalogModuleApi(restTemplate, catalogServiceUrl, gatewayTrustSecret),
        RemoteProviderModuleApi(restTemplate, providerServiceUrl, gatewayTrustSecret),
        RemotePaymentModuleApi(restTemplate, paymentServiceUrl, gatewayTrustSecret),
        holdDurationSeconds
    )

    private val logger = LoggerFactory.getLogger(AppointmentService::class.java)
    private val writeLockDuration = Duration.ofSeconds(10)
    private val holdDuration: Duration get() = Duration.ofSeconds(holdDurationSeconds)

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
        catalogModule.updateSlotStatus(slotId, status)
    }

    private fun fetchCatalogSlotStart(slotId: UUID): String? = try {
        catalogModule.slot(slotId)?.slotStart?.toString()
    } catch (error: Exception) {
        logger.warn("Failed to read slot start from catalog module: {}", error.message, error)
        null
    }

    private fun publishAppointmentBooked(saved: Appointment) {
        val event = AppointmentBookedEvent(
            actorId = saved.customerId,
            appointmentId = saved.appointmentId!!,
            customerId = saved.customerId,
            providerId = saved.providerId,
            slotId = saved.slotId,
            slotStart = fetchCatalogSlotStart(saved.slotId),
            priceAmount = saved.priceAmount
        )
        outboxService.saveEvent(
            eventId = event.eventId,
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
        val event = AppointmentStatusChangedEvent(
            actorId = actorId,
            appointmentId = appointment.appointmentId!!,
            slotId = appointment.slotId,
            fromStatus = fromStatus.name,
            toStatus = toStatus.name
        )
        outboxService.saveEvent(
            eventId = event.eventId,
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
            if (activeAppointmentExists(request.slotId)) throw IllegalStateException("Slot is already booked or held.")
            try {
                updateCatalogSlotStatus(request.slotId, "BOOKED")
            } catch (error: Exception) {
                throw IllegalStateException("Failed to update slot status in Catalog Service: ${error.message}", error)
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
            } catch (error: Exception) {
                rollbackSlot(request.slotId, "database failure")
                throw error
            }
            try {
                logStatusChange(saved.appointmentId!!, null, AppointmentStatus.CONFIRMED, saved.customerId, "Direct appointment booking")
                publishAppointmentBooked(saved)
            } catch (error: Exception) {
                rollbackSlot(request.slotId, "booking event logging failure")
                throw error
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
            if (activeAppointmentExists(request.slotId)) throw IllegalStateException("Slot is already booked or held.")
            try {
                updateCatalogSlotStatus(request.slotId, "HELD")
            } catch (error: Exception) {
                throw IllegalStateException("Failed to update slot status in Catalog Service: ${error.message}", error)
            }

            val saved = try {
                appointmentRepository.save(
                    Appointment(
                        customerId = request.customerId,
                        providerId = request.providerId,
                        offeringId = request.offeringId,
                        slotId = request.slotId,
                        petId = request.petId,
                        priceAmount = request.priceAmount,
                        status = AppointmentStatus.SLOT_HELD
                    )
                )
            } catch (error: Exception) {
                rollbackSlot(request.slotId, "database failure")
                throw error
            }
            try {
                logStatusChange(saved.appointmentId!!, null, AppointmentStatus.SLOT_HELD, saved.customerId, "Slot held for customer")
                redisTemplate.opsForValue().set(holdKey(saved.slotId), saved.appointmentId.toString(), holdDuration)
            } catch (error: Exception) {
                rollbackSlot(request.slotId, "status logging failure")
                throw error
            }
            return saved
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    fun confirmAppointment(
        appointmentId: UUID,
        paymentId: UUID?,
        callerId: UUID,
        callerRole: String?,
    ): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }
        assertCanAccessAppointment(appointment, callerId, callerRole)

        if (!appointment.payAtClinic) {
            if (paymentId == null) throw IllegalArgumentException("paymentId is required to confirm this appointment.")
            verifyAppointmentPayment(appointment, paymentId)
        }
        if (appointment.status != AppointmentStatus.SLOT_HELD) {
            throw IllegalStateException("Appointment is not in SLOT_HELD state. Current state: ${appointment.status}")
        }

        val cutoff = Instant.now().minusSeconds(holdDurationSeconds)
        if (appointment.bookedAt.isBefore(cutoff) || redisTemplate.hasKey(holdKey(appointment.slotId)) == false) {
            appointment.status = AppointmentStatus.EXPIRED
            appointmentRepository.save(appointment)
            logStatusChange(
                appointmentId,
                AppointmentStatus.SLOT_HELD,
                AppointmentStatus.EXPIRED,
                appointment.customerId,
                "Hold expired before confirmation"
            )
            redisTemplate.delete(holdKey(appointment.slotId))
            rollbackSlot(appointment.slotId, "expired hold")
            throw IllegalStateException("Slot hold has expired. Please select the slot and try again.")
        }

        try {
            updateCatalogSlotStatus(appointment.slotId, "BOOKED")
        } catch (error: Exception) {
            throw IllegalStateException("Failed to book slot in Catalog Service: ${error.message}", error)
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

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(
        name = "appointment_cleanupExpiredHolds",
        lockAtMostFor = "PT30S",
        lockAtLeastFor = "PT1S"
    )
    fun cleanupExpiredHolds() {
        val cutoff = Instant.now().minusSeconds(holdDurationSeconds)
        appointmentRepository.findByStatusAndBookedAtBefore(AppointmentStatus.SLOT_HELD, cutoff).forEach { appointment ->
            try {
                appointment.status = AppointmentStatus.EXPIRED
                appointmentRepository.save(appointment)
                logStatusChange(
                    appointment.appointmentId!!,
                    AppointmentStatus.SLOT_HELD,
                    AppointmentStatus.EXPIRED,
                    appointment.customerId,
                    "Slot hold expired"
                )
                redisTemplate.delete(holdKey(appointment.slotId))
                updateCatalogSlotStatus(appointment.slotId, "AVAILABLE")
                logger.info("Slot hold expired for appointment {}, slot {} is now AVAILABLE.", appointment.appointmentId, appointment.slotId)
            } catch (error: Exception) {
                logger.error("Failed to expire slot hold for appointment {}: {}", appointment.appointmentId, error.message, error)
            }
        }
    }

    fun fetchProviderOwnerUserId(providerId: UUID): UUID? = try {
        providerModule.ownerUserId(providerId)
    } catch (error: Exception) {
        logger.warn("Failed to fetch provider owner from provider module: {}", error.message, error)
        null
    }

    fun getAppointment(appointmentId: UUID, callerId: UUID, callerRole: String?): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }
        assertCanAccessAppointment(appointment, callerId, callerRole)
        return appointment
    }

    private fun assertCanAccessAppointment(appointment: Appointment, callerId: UUID, callerRole: String?) {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isCustomer = appointment.customerId == callerId
        val isProviderStaff = callerRole?.uppercase() == "MERCHANT" &&
            fetchProviderOwnerUserId(appointment.providerId) == callerId
        if (!isAdmin && !isCustomer && !isProviderStaff) {
            throw AppointmentAccessDeniedException("Access denied to appointment data.")
        }
    }

    private fun verifyAppointmentPayment(appointment: Appointment, paymentId: UUID) {
        val transaction = paymentModule.transaction(paymentId)
            ?: throw IllegalStateException("Payment transaction not found for ID $paymentId")
        if (transaction.status != "SUCCESS") throw IllegalStateException("Payment transaction is not successful.")
        if (transaction.referenceId != appointment.appointmentId) {
            throw IllegalStateException("Payment transaction does not match this appointment.")
        }
        if (transaction.userId != appointment.customerId) {
            throw IllegalStateException("Payment transaction does not belong to the appointment customer.")
        }
        if (transaction.transactionType != "APPOINTMENT_PAYMENT") {
            throw IllegalStateException("Payment transaction type is invalid for appointment confirmation.")
        }
        if (transaction.amount.compareTo(appointment.priceAmount) != 0) {
            throw IllegalStateException("Payment amount does not match appointment price.")
        }
    }

    fun getAppointmentsByCustomer(targetCustomerId: UUID, callerId: UUID, callerRole: String?): List<Appointment> {
        if (callerRole?.uppercase() != "ADMIN" && targetCustomerId != callerId) {
            throw AppointmentAccessDeniedException("Access denied to customer appointment history.")
        }
        return appointmentRepository.findByCustomerId(targetCustomerId)
    }

    fun getAppointmentsByProvider(providerId: UUID, callerId: UUID, callerRole: String?): List<Appointment> {
        val isAdmin = callerRole?.uppercase() == "ADMIN"
        val isProviderStaff = callerRole?.uppercase() == "MERCHANT" && fetchProviderOwnerUserId(providerId) == callerId
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
        val isCustomer = appointment.customerId == changedBy
        val isProviderStaff = callerRole?.uppercase() == "MERCHANT" &&
            fetchProviderOwnerUserId(appointment.providerId) == changedBy
        if (!isAdmin && !isProviderStaff && !(isCustomer && newStatus == AppointmentStatus.CANCELLED)) {
            throw AppointmentAccessDeniedException("Access denied to change appointment status.")
        }

        val oldStatus = appointment.status
        if (newStatus == AppointmentStatus.CANCELLED && oldStatus in setOf(
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.EXPIRED,
                AppointmentStatus.NO_SHOW
            )
        ) {
            throw IllegalStateException("Appointment in status $oldStatus cannot be cancelled.")
        }

        appointment.status = newStatus
        when (newStatus) {
            AppointmentStatus.COMPLETED -> {
                appointment.completedAt = Instant.now()
                prescriptionDocUrl?.let { appointment.prescriptionDocUrl = it }
            }
            AppointmentStatus.CANCELLED -> {
                appointment.cancelledAt = Instant.now()
                appointment.cancellationReason = note
                try {
                    updateCatalogSlotStatus(appointment.slotId, "AVAILABLE")
                    redisTemplate.delete(holdKey(appointment.slotId))
                } catch (error: Exception) {
                    logger.warn("Failed to set slot back to AVAILABLE in catalog module: {}", error.message, error)
                }
            }
            else -> Unit
        }

        val updated = appointmentRepository.save(appointment)
        logStatusChange(appointmentId, oldStatus, newStatus, changedBy, note)
        if (newStatus == AppointmentStatus.COMPLETED) generateInvoiceForAppointment(updated)
        publishAppointmentStatusChanged(updated, oldStatus, newStatus, changedBy)
        return updated
    }

    fun rescheduleAppointment(
        appointmentId: UUID,
        newSlotId: UUID,
        callerId: UUID,
        callerRole: String?
    ): Appointment {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { NoSuchElementException("Appointment with ID $appointmentId not found") }
        assertCanAccessAppointment(appointment, callerId, callerRole)
        if (appointment.status !in setOf(AppointmentStatus.SLOT_HELD, AppointmentStatus.CONFIRMED)) {
            throw IllegalStateException("Appointment in status ${appointment.status} cannot be rescheduled.")
        }
        val oldSlotId = appointment.slotId
        if (oldSlotId == newSlotId) return appointment
        if (activeAppointmentExists(newSlotId)) throw IllegalStateException("New slot is already booked or held.")

        try {
            updateCatalogSlotStatus(
                newSlotId,
                if (appointment.status == AppointmentStatus.CONFIRMED) "BOOKED" else "HELD"
            )
        } catch (error: Exception) {
            throw IllegalStateException("Failed to update new slot status in Catalog Service: ${error.message}", error)
        }
        try {
            updateCatalogSlotStatus(oldSlotId, "AVAILABLE")
            redisTemplate.delete(holdKey(oldSlotId))
        } catch (error: Exception) {
            logger.warn("Failed to release old slot {}: {}", oldSlotId, error.message, error)
        }

        appointment.slotId = newSlotId
        val updated = appointmentRepository.save(appointment)
        logStatusChange(appointmentId, appointment.status, appointment.status, callerId, "Rescheduled to new slot $newSlotId")
        return updated
    }

    fun getInvoiceByAppointmentId(appointmentId: UUID): AppointmentInvoice =
        appointmentInvoiceRepository.findByAppointmentId(appointmentId)
            .orElseThrow { NoSuchElementException("Invoice not found for appointment $appointmentId") }

    private fun rollbackSlot(slotId: UUID, reason: String) {
        try {
            updateCatalogSlotStatus(slotId, "AVAILABLE")
        } catch (rollbackError: Exception) {
            logger.error("Failed to revert slot status to AVAILABLE after {}: {}", reason, rollbackError.message, rollbackError)
        }
    }

    private fun logStatusChange(
        appointmentId: UUID,
        from: AppointmentStatus?,
        to: AppointmentStatus,
        by: UUID,
        note: String?
    ) {
        appointmentStatusHistoryRepository.save(
            AppointmentStatusHistory(
                appointmentId = appointmentId,
                fromStatus = from,
                toStatus = to,
                changedByUserId = by,
                note = note
            )
        )
    }

    private fun generateInvoiceForAppointment(appointment: Appointment) {
        val appointmentId = appointment.appointmentId ?: return
        if (appointmentInvoiceRepository.findByAppointmentId(appointmentId).isPresent) return
        val subtotal = appointment.priceAmount.setScale(2, RoundingMode.HALF_UP)
        val tax = subtotal.multiply(BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP)
        val total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP)
        appointmentInvoiceRepository.save(
            AppointmentInvoice(
                appointmentId = appointmentId,
                invoiceNumber = "APT-INV-${LocalDate.now().year}-${appointmentId.toString().take(8).uppercase()}",
                subtotalAmount = subtotal,
                taxAmount = tax,
                totalAmount = total
            )
        )
    }
}
