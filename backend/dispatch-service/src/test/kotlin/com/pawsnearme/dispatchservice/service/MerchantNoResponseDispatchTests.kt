package com.pawsnearme.dispatchservice.service

import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.dispatchservice.repository.DispatchJobRepository
import com.pawsnearme.dispatchservice.repository.DispatchOfferRepository
import jakarta.persistence.EntityManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.client.RestOperations
import java.util.UUID

class MerchantNoResponseDispatchTests {
    @Test
    fun `merchant non-response leaves placed order outside dispatch`() {
        val jobRepository: DispatchJobRepository = mock()
        val offerRepository: DispatchOfferRepository = mock()
        val redisTemplate: StringRedisTemplate = mock()
        val kafkaTemplate: KafkaTemplate<String, Any> = mock()
        val entityManager: EntityManager = mock()
        val outboxService: OutboxService = mock()
        val idempotencyService: IdempotencyService = mock()
        val restTemplate: RestOperations = mock()
        val service = DispatchService(
            jobRepository,
            offerRepository,
            redisTemplate,
            kafkaTemplate,
            entityManager,
            outboxService,
            idempotencyService,
            restTemplate = restTemplate,
        )
        val orderId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val record = ConsumerRecord(
            "orders.events",
            0,
            0L,
            orderId.toString(),
            """{"eventId":"$eventId","fromStatus":null,"toStatus":"PLACED","orderId":"$orderId"}""",
        )

        service.handleOrderStatusChanged(record)

        verify(idempotencyService, never()).checkAndRecord(any<String>(), any())
        verify(jobRepository, never()).findByOrderId(any())
        verify(jobRepository, never()).save(any())
    }
}
