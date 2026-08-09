package com.pawsnearme.catalogservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.outbox.JpaOutboxPersistence
import com.pawsnearme.common.outbox.OutboxEventPublisherFactory
import com.pawsnearme.common.outbox.OutboxPoller
import com.pawsnearme.common.outbox.OutboxRepository
import com.pawsnearme.common.outbox.OutboxService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.kafka.core.KafkaTemplate

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = ["com.pawsnearme.catalogservice", "com.pawsnearme.common.outbox"])
@EnableJpaRepositories(basePackages = ["com.pawsnearme.catalogservice", "com.pawsnearme.common.outbox"])
@Import(OutboxService::class, JpaOutboxPersistence::class)
class OutboxConfig {
    @Bean
    fun outboxPoller(
        outboxRepository: OutboxRepository,
        kafkaTemplate: KafkaTemplate<String, Any>,
        objectMapper: ObjectMapper,
        applicationEventPublisher: ApplicationEventPublisher,
        @Value("\${mypet.events.delivery-mode:KAFKA_ONLY}") deliveryMode: String,
    ): OutboxPoller = OutboxPoller(
        outboxRepository,
        OutboxEventPublisherFactory.create(
            kafkaTemplate,
            objectMapper,
            applicationEventPublisher,
            deliveryMode,
        ),
    )
}
