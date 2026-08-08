package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.notificationservice.event.AppointmentEvent
import com.pawsnearme.notificationservice.model.ScheduledReminder
import com.pawsnearme.notificationservice.repository.ScheduledReminderRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AppointmentEventListener(
    private val reminderRepo: ScheduledReminderRepository,
    private val objectMapper: ObjectMapper,
    private val idempotencyService: IdempotencyService,
    private val transactionalEmailService: TransactionalEmailService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
        dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = ["appointments.events"], groupId = "notification-service")
    @Transactional
    fun onAppointmentEvent(message: String) {
        val event = runCatching {
            objectMapper.readValue(message, AppointmentEvent::class.java)
        }.getOrNull() ?: return log.warn("Could not parse appointment event: $message")

        if (!idempotencyService.checkAndRecord(event.eventId)) {
            log.info("NotificationService: Duplicate event ignored: ${event.eventId}")
            return
        }

        val isConfirmedBooking = event.eventType == "AppointmentBooked" ||
            (event.eventType == "AppointmentStatusChanged" && event.toStatus == "CONFIRMED")
        if (!isConfirmedBooking) return

        val customerId = event.customerId
        if (customerId == null) {
            log.debug(
                "Appointment event {} for {} has no customer_id; the corresponding AppointmentBooked event owns customer notifications",
                event.eventType,
                event.appointmentId,
            )
            return
        }

        if (event.eventType == "AppointmentBooked") {
            transactionalEmailService.registerReferenceOwner("APPOINTMENT", event.appointmentId, customerId)
            transactionalEmailService.enqueueForUser(
                userId = customerId,
                templateCode = "APPOINTMENT_BOOKED",
                idempotencyKey = "appointment:${event.appointmentId}:booked",
                variables = mapOf(
                    "appointment_id" to event.appointmentId.toString(),
                    "appointment_short_id" to event.appointmentId.toString().take(8),
                    "slot_start" to (event.slotStart?.toString() ?: ""),
                    "price_amount" to (event.priceAmount?.toPlainString() ?: ""),
                    "provider_id" to (event.providerId?.toString() ?: ""),
                ),
            )
        }

        val slotStart = event.slotStart ?: return log.warn(
            "Cannot schedule reminders for appointment {} because slot_start is missing",
            event.appointmentId
        )

        log.info("Scheduling reminders for appointment ${event.appointmentId}")
        scheduleReminder(event, customerId, "APPOINTMENT_T24H", slotStart.minusSeconds(24 * 3600))
        scheduleReminder(event, customerId, "APPOINTMENT_T1H", slotStart.minusSeconds(3600))
    }

    private fun scheduleReminder(event: AppointmentEvent, customerId: java.util.UUID, templateCode: String, fireAt: Instant) {
        if (reminderRepo.existsByReferenceIdAndTemplateCode(event.appointmentId, templateCode)) {
            log.debug("Reminder $templateCode already exists for ${event.appointmentId}, skipping")
            return
        }
        if (fireAt.isBefore(Instant.now())) {
            log.debug("Reminder $templateCode fire-at $fireAt is in the past, skipping")
            return
        }
        reminderRepo.save(
            ScheduledReminder(
                userId = customerId,
                referenceType = "APPOINTMENT",
                referenceId = event.appointmentId,
                fireAt = fireAt,
                templateCode = templateCode
            )
        )
        log.info("Saved $templateCode reminder for appointment ${event.appointmentId}, fires at $fireAt")
    }
}
