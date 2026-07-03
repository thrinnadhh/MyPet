package com.pawsnearme.orderservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.outbox.OutboxPoller
import com.pawsnearme.common.outbox.OutboxRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class OutboxConfig {

    @Bean
    fun outboxPoller(
        outboxRepository: OutboxRepository,
        kafkaTemplate: KafkaTemplate<String, Any>,
        objectMapper: ObjectMapper
    ): OutboxPoller = OutboxPoller(outboxRepository, kafkaTemplate, objectMapper)
}
