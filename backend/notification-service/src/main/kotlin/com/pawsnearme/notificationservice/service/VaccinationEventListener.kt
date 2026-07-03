package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.notificationservice.event.VaccinationReminderEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VaccinationEventListener(
    private val objectMapper: ObjectMapper,
    private val idempotencyService: IdempotencyService,
    private val vaccinationReminderScheduler: VaccinationReminderScheduler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
        dltTopicSuffix = ".dlq",
    )
    @KafkaListener(topics = ["vaccination.events"], groupId = "notification-service-vaccination")
    @Transactional
    fun onVaccinationEvent(message: String) {
        val event = runCatching {
            objectMapper.readValue(message, VaccinationReminderEvent::class.java)
        }.getOrNull() ?: return log.warn("Could not parse vaccination event: $message")

        if (!idempotencyService.checkAndRecord(event.eventId)) {
            log.info("Duplicate vaccination event ignored: {}", event.eventId)
            return
        }

        vaccinationReminderScheduler.apply(event)
    }
}
