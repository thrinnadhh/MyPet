package com.pawsnearme.providerservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReviewEventListener(
    private val providerService: ProviderService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["reviews.events"], groupId = "provider-service-reviews")
    @Transactional
    fun onReviewEvent(message: String) {
        log.info("Received review event message: {}", message)
        val event = runCatching {
            objectMapper.readValue(message, Map::class.java)
        }.getOrElse {
            log.error("Failed to parse review event payload: {}", message, it)
            return
        }

        val eventType = event["event_type"] as? String
        if (eventType == "ReviewSubmitted") {
            val providerIdStr = event["provider_id"] as? String
            val ratingNum = event["rating"] as? Number
            if (providerIdStr != null && ratingNum != null) {
                val providerId = UUID.fromString(providerIdStr)
                val rating = ratingNum.toInt()
                log.info("Applying review rating {} to provider {}", rating, providerId)
                runCatching {
                    providerService.updateProviderRating(providerId, rating)
                }.onFailure {
                    log.warn("Skipping ReviewSubmitted event for unknown provider {}", providerId, it)
                }
            } else {
                log.warn("Invalid ReviewSubmitted event payload - provider_id: {}, rating: {}", providerIdStr, ratingNum)
            }
        }
    }
}
