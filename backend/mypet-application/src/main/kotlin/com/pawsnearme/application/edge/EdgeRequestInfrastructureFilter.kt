package com.pawsnearme.application.edge

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min

class EdgeRequestInfrastructureFilter(
    private val properties: EdgeSecurityProperties
) : OncePerRequestFilter() {

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        private val validRequestId = Regex("[A-Za-z0-9._:-]{1,128}")
    }

    private val rateLimiter = InMemoryTokenBucketRateLimiter(properties.rateLimit)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.trim()
            ?.takeIf(validRequestId::matches)
            ?: UUID.randomUUID().toString()

        response.setHeader(REQUEST_ID_HEADER, requestId)
        MDC.put("requestId", requestId)

        val wrappedRequest = MutableHeadersRequest(
            request,
            removedHeaders = setOf(REQUEST_ID_HEADER),
            replacementHeaders = mapOf(REQUEST_ID_HEADER to requestId)
        )

        try {
            if (shouldApplyRateLimit(wrappedRequest)) {
                val decision = rateLimiter.acquire(clientKey(wrappedRequest))
                response.setHeader("X-RateLimit-Remaining", decision.remaining.toString())
                response.setHeader(
                    "X-RateLimit-Replenish-Rate",
                    properties.rateLimit.replenishRate.toString()
                )
                response.setHeader(
                    "X-RateLimit-Burst-Capacity",
                    properties.rateLimit.burstCapacity.toString()
                )

                if (!decision.allowed) {
                    response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                    response.contentType = "application/json"
                    response.setHeader("Retry-After", decision.retryAfterSeconds.toString())
                    response.writer.write("""{"error":"Too many requests"}""")
                    return
                }
            }

            filterChain.doFilter(wrappedRequest, response)
        } finally {
            MDC.remove("requestId")
        }
    }

    private fun shouldApplyRateLimit(request: HttpServletRequest): Boolean =
        properties.rateLimit.enabled &&
            !request.method.equals("OPTIONS", ignoreCase = true) &&
            !request.requestURI.startsWith("/actuator/")

    private fun clientKey(request: HttpServletRequest): String {
        val forwarded = if (properties.rateLimit.trustForwardedFor) {
            request.getHeader("X-Forwarded-For")
                ?.substringBefore(",")
                ?.trim()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }

        return forwarded ?: request.remoteAddr?.takeIf(String::isNotBlank) ?: "unknown"
    }
}

data class RateLimitDecision(
    val allowed: Boolean,
    val remaining: Int,
    val retryAfterSeconds: Long
)

class InMemoryTokenBucketRateLimiter(
    private val properties: EdgeSecurityProperties.RateLimitProperties
) {
    private data class Bucket(var tokens: Double, var lastRefillNanos: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    init {
        require(properties.replenishRate > 0) { "Rate-limit replenish rate must be positive" }
        require(properties.burstCapacity > 0) { "Rate-limit burst capacity must be positive" }
        require(properties.maxClients > 0) { "Rate-limit max clients must be positive" }
    }

    fun acquire(key: String, nowNanos: Long = System.nanoTime()): RateLimitDecision {
        var result: RateLimitDecision? = null

        buckets.compute(key) { _, current ->
            val bucket = current ?: Bucket(properties.burstCapacity.toDouble(), nowNanos)
            val elapsedSeconds = ((nowNanos - bucket.lastRefillNanos).coerceAtLeast(0L)) / 1_000_000_000.0
            bucket.tokens = min(
                properties.burstCapacity.toDouble(),
                bucket.tokens + elapsedSeconds * properties.replenishRate
            )
            bucket.lastRefillNanos = nowNanos

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                result = RateLimitDecision(
                    allowed = true,
                    remaining = bucket.tokens.toInt(),
                    retryAfterSeconds = 0
                )
            } else {
                val retryAfter = ceil((1.0 - bucket.tokens) / properties.replenishRate)
                    .toLong()
                    .coerceAtLeast(1)
                result = RateLimitDecision(
                    allowed = false,
                    remaining = 0,
                    retryAfterSeconds = retryAfter
                )
            }
            bucket
        }

        if (buckets.size > properties.maxClients) {
            buckets.keys.firstOrNull { it != key }?.let(buckets::remove)
        }

        return requireNotNull(result)
    }

    fun clear() = buckets.clear()
}
