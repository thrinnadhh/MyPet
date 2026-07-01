package com.pawsnearme.apigateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    private val allowUnsignedJwt: Boolean
) {

    @Bean
    fun corsConfigurationSource(): org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource {
        val config = org.springframework.web.cors.CorsConfiguration()
        config.allowCredentials = true
        config.allowedOriginPatterns = listOf("*")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        val source = org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { exchange ->
                exchange
                    .pathMatchers("/api/v1/discovery/**").permitAll()      // Publicly searchable providers
                    .pathMatchers("/api/v1/reviews/provider/**").permitAll() // Publicly viewable reviews
                    .pathMatchers("/actuator/**").permitAll()              // Health check, etc.
                    .anyExchange().permitAll()                             // For local dev sandbox convenience, permit all
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
        return http.build()
    }

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder {
        return when {
            secretKey.isNotBlank() -> {
                // Symmetric key validation for HS256 (e.g., Supabase JWT Secret)
                val secretKeySpec = SecretKeySpec(
                    secretKey.toByteArray(),
                    "HMAC"
                )
                NimbusReactiveJwtDecoder.withSecretKey(secretKeySpec)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
            }
            jwkSetUri.isNotBlank() && !jwkSetUri.contains("your-project.supabase.co") -> {
                // JWK endpoint validation for Supabase asymmetric JWTs.
                // Supabase projects can issue ES256 or RS256 tokens depending
                // on their signing key configuration.
                NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                    .jwsAlgorithms {
                        it.add(SignatureAlgorithm.ES256)
                        it.add(SignatureAlgorithm.RS256)
                    }
                    .build()
            }
            allowUnsignedJwt -> {
                println("WARNING: Supabase Auth is running in mock/dev mode. JWT signatures are NOT validated.")
                ReactiveJwtDecoder { jwtString ->
                    try {
                        val jwt = com.nimbusds.jwt.JWTParser.parse(jwtString)
                        val claims = jwt.jwtClaimsSet.claims
                        val headers = jwt.header.toJSONObject()
                        val springJwt = org.springframework.security.oauth2.jwt.Jwt(
                            jwtString,
                            jwt.jwtClaimsSet.issueTime?.toInstant() ?: Instant.now(),
                            jwt.jwtClaimsSet.expirationTime?.toInstant() ?: Instant.now().plusSeconds(3600),
                            headers,
                            claims
                        )
                        reactor.core.publisher.Mono.just(springJwt)
                    } catch (e: Exception) {
                        reactor.core.publisher.Mono.error(e)
                    }
                }
            }
            else -> {
                throw IllegalStateException("JWT validation is not configured. Set SUPABASE_JWT_SECRET, SUPABASE_JWT_JWK_SET_URI, or ALLOW_UNSIGNED_JWT=true for local-only development.")
            }
        }
    }
}
