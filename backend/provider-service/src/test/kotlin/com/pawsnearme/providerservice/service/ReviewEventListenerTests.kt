package com.pawsnearme.providerservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.repository.ProfileRepository
import com.pawsnearme.providerservice.repository.ProviderDocumentRepository
import com.pawsnearme.providerservice.repository.ProviderRepository
import com.pawsnearme.providerservice.repository.UserRoleJoinRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ReviewEventListenerTests {
    private val providerRepository: ProviderRepository = mock()
    private val providerDocumentRepository: ProviderDocumentRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val userRoleJoinRepository: UserRoleJoinRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val outboxService: com.pawsnearme.common.outbox.OutboxService = mock()
    private val providerService = ProviderService(
        providerRepository,
        providerDocumentRepository,
        profileRepository,
        userRoleJoinRepository,
        kafkaTemplate,
        outboxService
    )
    private val listener = ReviewEventListener(providerService, ObjectMapper())
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `ReviewSubmitted event updates provider aggregate rating`() {
        val providerId = UUID.randomUUID()
        val provider = sampleProvider(providerId).apply {
            ratingAvg = BigDecimal("4.00")
            ratingCount = 2
        }
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))
        whenever(providerRepository.save(any())).thenAnswer { it.arguments[0] as Provider }

        listener.onReviewEvent(
            """
            {
              "event_id": "${UUID.randomUUID()}",
              "event_type": "ReviewSubmitted",
              "occurred_at": "2026-07-03T10:00:00Z",
              "customer_id": "${UUID.randomUUID()}",
              "provider_id": "$providerId",
              "target_type": "ORDER",
              "target_id": "${UUID.randomUUID()}",
              "rating": 5
            }
            """.trimIndent()
        )

        assertEquals(3, provider.ratingCount)
        assertEquals(BigDecimal("4.33"), provider.ratingAvg)
    }

    @Test
    fun `ReviewSubmitted event for missing provider is skipped without throwing`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.empty())

        listener.onReviewEvent(
            """
            {
              "event_id": "${UUID.randomUUID()}",
              "event_type": "ReviewSubmitted",
              "occurred_at": "2026-07-03T10:00:00Z",
              "customer_id": "${UUID.randomUUID()}",
              "provider_id": "$providerId",
              "target_type": "ORDER",
              "target_id": "${UUID.randomUUID()}",
              "rating": 5
            }
            """.trimIndent()
        )
    }

    private fun sampleProvider(providerId: UUID) = Provider(
        providerId = providerId,
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Happy Tails",
        addressLine = "12 Main Road",
        city = "Bengaluru",
        pincode = "560001",
        geoLocation = geometryFactory.createPoint(Coordinate(77.5946, 12.9716)),
        status = ProviderStatus.ACTIVE,
        commissionPct = BigDecimal("15.00")
    )
}
