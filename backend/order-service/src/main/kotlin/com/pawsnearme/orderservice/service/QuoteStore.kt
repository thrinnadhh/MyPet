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
    val items: List<QuoteItemSnapshot> = emptyList()
)

/**
 * Stores and validates checkout quote tokens in Redis with a 15-minute TTL.
 *
 * Enforces immutable binding of total, customerId, providerId, paymentMethod,
 * and items snapshot to the token.
 */
@Component
class QuoteStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val KEY_PREFIX = "quote:"
        private val TTL = Duration.ofMinutes(15)
    }

    /** Persist a quote snapshot. Returns the stored token. */
    fun store(token: String, snapshot: QuoteSnapshot): String {
        val payload = objectMapper.writeValueAsString(snapshot)
        redisTemplate.opsForValue().set(key(token), payload, TTL)
        return token
    }

    /** Backward compatible helper overload. */
    fun store(token: String, total: BigDecimal, couponCode: String?): String {
        val dummyId = UUID.randomUUID()
        return store(
            token,
            QuoteSnapshot(
                total = total,
                couponCode = couponCode,
                customerId = dummyId,
                providerId = dummyId,
                paymentMethod = "CARD",
                items = emptyList()
            )
        )
    }

    /**
     * Validate a token and return the locked-in quote snapshot.
     * Returns null if the token is expired, unknown, or was already consumed.
     */
    fun consume(token: String): QuoteSnapshot? {
        val raw = redisTemplate.opsForValue().getAndDelete(key(token)) ?: return null
        return try {
            objectMapper.readValue(raw, QuoteSnapshot::class.java)
        } catch (e: Exception) {
            val map = objectMapper.readValue(raw, Map::class.java)
            val total = BigDecimal(map["total"] as String)
            val couponCode = (map["couponCode"] as? String)?.takeIf { it.isNotBlank() }
            val dummyId = UUID.randomUUID()
            QuoteSnapshot(
                total = total,
                couponCode = couponCode,
                customerId = dummyId,
                providerId = dummyId,
                paymentMethod = "CARD",
                items = emptyList()
            )
        }
    }

    private fun key(token: String) = "$KEY_PREFIX$token"
}
