package com.pawsnearme.apigateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
class GatewayRateLimitConfig {
    @Bean
    fun clientIpKeyResolver(): KeyResolver = KeyResolver { exchange ->
        val forwardedFor = exchange.request.headers.getFirst("X-Forwarded-For")
            ?.substringBefore(",")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val remoteAddress = exchange.request.remoteAddress?.address?.hostAddress
        Mono.just(forwardedFor ?: remoteAddress ?: "unknown")
    }
}
