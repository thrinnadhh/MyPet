package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

data class QuoteItemSnapshot(
    val offeringId: UUID,
    val quantity: Int
)

data class QuoteSnapshot(
    val total: BigDecimal,
    val couponCode: String?,
    val customerId: UUID,
    val providerId: UUID,
    val paymentMethod: String,
    val deliveryAddressId: UUID,
    val loyaltyRewardId: UUID?,
    val items: List<QuoteItemSnapshot>
)

@Component
class QuoteStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val KEY_PREFIX = "quote:"
        private val TTL = Duration.ofMinutes(15)
    }

    fun store(token: String, snapshot: QuoteSnapshot): String {
        redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(snapshot), TTL)
        return token
    }

    /** Atomic read-and-delete. A quote can authorize exactly one order attempt. */
    fun consume(token: String): QuoteSnapshot? {
        val raw = redisTemplate.opsForValue().getAndDelete(key(token)) ?: return null
        return objectMapper.readValue(raw, QuoteSnapshot::class.java)
    }

    fun delete(token: String) {
        redisTemplate.delete(key(token))
    }

    private fun key(token: String) = "$KEY_PREFIX$token"
}
