package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration

/**
 * Stores and validates checkout quote tokens in Redis with a 15-minute TTL.
 *
 * ## Why this matters (P0-5)
 * Without persistent quote storage, a client can:
 *  1. Call GET /checkout/quote at a low price
 *  2. Wait for a price rise
 *  3. Call POST /orders with `quoteToken` from step 1 and omit the token
 *     (order-service ignores it and recalculates), paying the new price
 *
 * Or worse, replay an old quote token across multiple simultaneous orders
 * to submit duplicate orders at the same total.
 *
 * Storing the quote in Redis:
 * - Binds the confirmed total to the token
 * - Enforces a 15-minute expiry (matching the 900 s `expiresAt` in the response)
 * - Allows createOrder() to use the pre-approved total instead of recalculating
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
    fun store(token: String, total: BigDecimal, couponCode: String?): String {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "total" to total.toPlainString(),
                "couponCode" to (couponCode ?: "")
            )
        )
        redisTemplate.opsForValue().set(key(token), payload, TTL)
        return token
    }

    /**
     * Validate a token and return the locked-in total.
     * Returns null if the token is expired, unknown, or was already consumed.
     */
    fun consume(token: String): QuoteSnapshot? {
        val raw = redisTemplate.opsForValue().getAndDelete(key(token)) ?: return null
        val map = objectMapper.readValue(raw, Map::class.java)
        val total = BigDecimal(map["total"] as String)
        val couponCode = (map["couponCode"] as? String)?.takeIf { it.isNotBlank() }
        return QuoteSnapshot(total, couponCode)
    }

    private fun key(token: String) = "$KEY_PREFIX$token"

    data class QuoteSnapshot(val total: BigDecimal, val couponCode: String?)
}
