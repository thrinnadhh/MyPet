package com.pawsnearme.apigateway.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private val jwkSetUri: String,
    @Value("\${spring.security.oauth2.resourceserver.jwt.secret-key:}")
    private val secretKey: String,
    @Value("\${spring.security.oauth2.resourceserver.jwt.allow-unsigned:false}")
    private val allowUnsignedJwt: Boolean,
    @Value("\${gateway.cors.allowed-origins:http://localhost:3000,http://localhost:8081}")
    private val corsAllowedOrigins: String
) {
    @Bean
    fun corsConfigurationSource(): org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource {
        val config = org.springframework.web.cors.CorsConfiguration()
        val origins = corsAllowedOrigins.split(",").map(String::trim).filter(String::isNotBlank).distinct()
        require(origins.isNotEmpty()) { "At least one gateway CORS origin must be configured" }
        require("*" !in origins) { "Wildcard CORS origins are not allowed. Set GATEWAY_CORS_ALLOWED_ORIGINS to explicit origins." }
        config.allowCredentials = true
        config.allowedOrigins = origins
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With", "X-Razorpay-Signature")
        config.exposedHeaders = listOf("Location", "Retry-After")
        config.maxAge = 3600
        return org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { exchange ->
                exchange
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/v1/discovery/**").permitAll()
                    .pathMatchers("/api/v1/reviews/provider/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/providers/*").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/catalog/offerings/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/catalog/slots/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/content/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/service-regions/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/appointments/medical-documents/*/content").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/orders/customer-cases/evidence/*/content").permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                    .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 -> oauth2.jwt { } }
        return http.build()
    }

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder = when {
        secretKey.isNotBlank() -> NimbusReactiveJwtDecoder.withSecretKey(SecretKeySpec(secretKey.toByteArray(), "HMAC"))
            .macAlgorithm(MacAlgorithm.HS256).build()
        jwkSetUri.isNotBlank() && !jwkSetUri.contains("your-project.supabase.co") ->
            NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithms {
                it.add(SignatureAlgorithm.ES256)
                it.add(SignatureAlgorithm.RS256)
            }.build()
        allowUnsignedJwt -> {
            val activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: ""
            val isSafeProfile = setOf("local", "dev", "test").any { activeProfiles.contains(it, ignoreCase = true) }
            if (!isSafeProfile) {
                throw IllegalStateException(
                    "ALLOW_UNSIGNED_JWT=true is only permitted with SPRING_PROFILES_ACTIVE=local|dev|test. " +
                        "Current profile: '$activeProfiles'. Remove ALLOW_UNSIGNED_JWT from production configuration."
                )
            }
            logger.warn("Supabase Auth is running in explicit local-only mode. JWT signatures are NOT validated.")
            ReactiveJwtDecoder { jwtString ->
                try {
                    val jwt = com.nimbusds.jwt.JWTParser.parse(jwtString)
                    val springJwt = org.springframework.security.oauth2.jwt.Jwt(
                        jwtString,
                        jwt.jwtClaimsSet.issueTime?.toInstant() ?: Instant.now(),
                        jwt.jwtClaimsSet.expirationTime?.toInstant() ?: Instant.now().plusSeconds(3600),
                        jwt.header.toJSONObject(),
                        jwt.jwtClaimsSet.claims
                    )
                    reactor.core.publisher.Mono.just(springJwt)
                } catch (error: Exception) {
                    reactor.core.publisher.Mono.error(error)
                }
            }
        }
        else -> throw IllegalStateException(
            "JWT validation is not configured. Set SUPABASE_JWT_SECRET, " +
                "SUPABASE_JWT_JWK_SET_URI, or ALLOW_UNSIGNED_JWT=true for local-only development."
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)
    }
}
