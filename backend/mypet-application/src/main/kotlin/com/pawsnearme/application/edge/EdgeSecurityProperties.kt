package com.pawsnearme.application.edge

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "mypet.edge")
class EdgeSecurityProperties {
    var enabled: Boolean = false
    var jwt: JwtProperties = JwtProperties()
    var cors: CorsProperties = CorsProperties()
    var rateLimit: RateLimitProperties = RateLimitProperties()
    var idempotency: IdempotencyProperties = IdempotencyProperties()

    class JwtProperties {
        var jwkSetUri: String = ""
        var secretKey: String = ""
        var allowUnsigned: Boolean = false
    }

    class CorsProperties {
        var allowedOrigins: String = "http://localhost:3000,http://localhost:8081"

        fun parsedAllowedOrigins(): List<String> = allowedOrigins
            .split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    class RateLimitProperties {
        var enabled: Boolean = true
        var replenishRate: Int = 100
        var burstCapacity: Int = 200
        var trustForwardedFor: Boolean = false
        var maxClients: Int = 20_000
    }

    class IdempotencyProperties {
        var enabled: Boolean = true
        var ttlSeconds: Long = 900
        var maxEntries: Int = 10_000
        var maxBodyBytes: Int = 1_048_576
    }
}
