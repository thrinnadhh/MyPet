package com.pawsnearme.paymentservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.outbox.OutboxEventPublisherFactory
import com.pawsnearme.common.outbox.OutboxPoller
import com.pawsnearme.common.outbox.OutboxRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class OutboxConfig {

    @Bean
    fun outboxPoller(
        outboxRepository: OutboxRepository,
        kafkaTemplate: KafkaTemplate<String, Any>,
        objectMapper: ObjectMapper,
        applicationEventPublisher: ApplicationEventPublisher,
        @Value("\${mypet.events.delivery-mode:KAFKA_ONLY}") deliveryMode: String
    ): OutboxPoller = OutboxPoller(
        outboxRepository,
        OutboxEventPublisherFactory.create(
            kafkaTemplate,
            objectMapper,
            applicationEventPublisher,
            deliveryMode
        )
    )
}
