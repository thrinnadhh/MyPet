package com.pawsnearme.application.edge

import com.nimbusds.jwt.JWTParser
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EdgeSecurityProperties::class)
class EdgeSecurityPropertiesConfiguration {

    @Bean
    fun edgeSecurityInfoContributor(properties: EdgeSecurityProperties): InfoContributor =
        object : InfoContributor {
            override fun contribute(builder: Info.Builder) {
                builder.withDetail(
                    "edgeSecurity",
                    mapOf(
                        "enabled" to properties.enabled,
                        "mode" to if (properties.enabled) "application-boundary" else "shadow-ready",
                        "jwt" to "configured-at-runtime",
                        "requestIds" to true,
                        "rateLimit" to properties.rateLimit.enabled,
                        "idempotency" to properties.idempotency.enabled
                    )
                )
            }
        }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "mypet.edge",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class DisabledEdgeSecurityConfiguration {

    @Bean
    fun disabledEdgeSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mypet.edge", name = ["enabled"], havingValue = "true")
class EnabledEdgeSecurityConfiguration(
    private val properties: EdgeSecurityProperties,
    private val environment: Environment
) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwt = properties.jwt
        return when {
            jwt.secretKey.isNotBlank() -> {
                val secretBytes = jwt.secretKey.toByteArray(Charsets.UTF_8)
                require(secretBytes.size >= 32) {
                    "MYPET JWT secret must be at least 32 bytes for HS256"
                }
                NimbusJwtDecoder.withSecretKey(SecretKeySpec(secretBytes, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
            }
            jwt.jwkSetUri.isNotBlank() && !jwt.jwkSetUri.contains("your-project.supabase.co") -> {
                NimbusJwtDecoder.withJwkSetUri(jwt.jwkSetUri)
                    .jwsAlgorithms {
                        it.add(SignatureAlgorithm.ES256)
                        it.add(SignatureAlgorithm.RS256)
                    }
                    .build()
            }
            jwt.allowUnsigned -> unsignedDevelopmentDecoder()
            else -> throw IllegalStateException(
                "Application edge security is enabled but JWT validation is not configured. " +
                    "Set SUPABASE_JWT_SECRET, SUPABASE_JWT_JWK_SET_URI, or use unsigned mode only in local/dev/test."
            )
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = properties.cors.parsedAllowedOrigins()
        require(origins.isNotEmpty()) { "At least one application CORS origin must be configured" }
        require("*" !in origins) {
            "Wildcard CORS origins are forbidden when credentials are enabled"
        }

        val configuration = CorsConfiguration().apply {
            allowCredentials = true
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-Razorpay-Signature",
                EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER,
                IdempotencyFilter.IDEMPOTENCY_KEY_HEADER
            )
            exposedHeaders = listOf(
                "Location",
                "Retry-After",
                EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER,
                "X-RateLimit-Remaining",
                "X-RateLimit-Replenish-Rate",
                "X-RateLimit-Burst-Capacity",
                IdempotencyFilter.REPLAYED_HEADER
            )
            maxAge = 3_600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    @Bean
    fun inMemoryIdempotencyStore(): InMemoryIdempotencyStore =
        InMemoryIdempotencyStore(properties.idempotency)

    @Bean
    fun edgeRequestInfrastructureFilter(): EdgeRequestInfrastructureFilter =
        EdgeRequestInfrastructureFilter(properties)

    @Bean
    fun suspendedUserFilter(redisTemplate: StringRedisTemplate): SuspendedUserFilter =
        SuspendedUserFilter(redisTemplate)

    @Bean
    fun identityHeaderFilter(): IdentityHeaderFilter = IdentityHeaderFilter()

    @Bean
    fun idempotencyFilter(store: InMemoryIdempotencyStore): IdempotencyFilter =
        IdempotencyFilter(properties, store)

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, out AbstractAuthenticationToken> {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            listOf<GrantedAuthority>(
                SimpleGrantedAuthority("ROLE_${JwtIdentityExtractor.extractRole(jwt)}")
            )
        }
        return converter
    }

    @Bean
    fun enabledEdgeSecurityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
        jwtAuthenticationConverter: Converter<Jwt, out AbstractAuthenticationToken>,
        edgeRequestInfrastructureFilter: EdgeRequestInfrastructureFilter,
        suspendedUserFilter: SuspendedUserFilter,
        identityHeaderFilter: IdentityHeaderFilter,
        idempotencyFilter: IdempotencyFilter
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorization ->
                authorization
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/providers/*/approve").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/providers/*/commission").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/providers/pending").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/providers").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/providers/*/documents", "/api/v1/providers/*/submit").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/profiles/*/revoke", "/api/v1/profiles/*/restore").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/profiles").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/catalog/offerings/**", "/api/v1/catalog/slots/**").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/catalog/offerings/**", "/api/v1/catalog/slots/**").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/catalog/offerings/**", "/api/v1/catalog/slots/**").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers("/api/v1/catalog/bills/**", "/api/v1/catalog/offerings/by-barcode/**").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers("/api/v1/admin/service-regions", "/api/v1/admin/service-regions/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/orders/admin/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/disputes").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/disputes/*/resolve").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/provider/*").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*/status").hasAnyRole("MERCHANT", "CAPTAIN", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/appointments/*/status").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/payouts/calculate").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/promotions").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/refund").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/chat/conversations/*/privacy").hasAnyRole("MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/content/guides").hasAnyRole("ADMIN", "MERCHANT")
                    .requestMatchers(HttpMethod.POST, "/api/v1/content/banners").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/content/guides/writers", "/api/v1/content/guides/writers/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/content/guides/writers", "/api/v1/content/guides/writers/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/captains/pending").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/captains/*/approve", "/api/v1/captains/*/reject").hasRole("ADMIN")
                    .requestMatchers("/api/v1/discovery/**").permitAll()
                    .requestMatchers("/api/v1/reviews/provider/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/providers/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/catalog/offerings/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/catalog/slots/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/content/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/service-regions/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    writeSecurityError(response, HttpStatus.UNAUTHORIZED, "Authentication required")
                }
                exceptions.accessDeniedHandler { _, response, _ ->
                    writeSecurityError(response, HttpStatus.FORBIDDEN, "Access denied: insufficient role")
                }
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }
            .addFilterBefore(edgeRequestInfrastructureFilter, BearerTokenAuthenticationFilter::class.java)
            .addFilterAfter(suspendedUserFilter, BearerTokenAuthenticationFilter::class.java)
            .addFilterAfter(identityHeaderFilter, SuspendedUserFilter::class.java)
            .addFilterAfter(idempotencyFilter, IdentityHeaderFilter::class.java)

        return http.build()
    }

    private fun unsignedDevelopmentDecoder(): JwtDecoder {
        val safeProfiles = setOf("local", "dev", "test")
        val activeProfiles = environment.activeProfiles.map(String::lowercase).toSet()
        require(activeProfiles.any(safeProfiles::contains)) {
            "Unsigned JWT mode is allowed only with an active local, dev or test profile"
        }
        logger.warn("Application edge security is using explicit unsigned JWT mode for {}", activeProfiles)

        return JwtDecoder { token ->
            try {
                val parsed = JWTParser.parse(token)
                val claimsSet = parsed.jwtClaimsSet
                val issuedAt = claimsSet.issueTime?.toInstant() ?: Instant.now()
                val expiresAt = claimsSet.expirationTime?.toInstant() ?: Instant.now().plusSeconds(3_600)
                if (expiresAt.isBefore(Instant.now())) throw JwtException("JWT is expired")
                Jwt(token, issuedAt, expiresAt, parsed.header.toJSONObject(), claimsSet.claims)
            } catch (error: JwtException) {
                throw error
            } catch (error: Exception) {
                throw JwtException("Unable to parse JWT", error)
            }
        }
    }

    private fun writeSecurityError(
        response: jakarta.servlet.http.HttpServletResponse,
        status: HttpStatus,
        message: String
    ) {
        if (response.isCommitted) return
        response.status = status.value()
        response.contentType = "application/json"
        response.writer.write("""{"error":"$message"}""")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EnabledEdgeSecurityConfiguration::class.java)
    }
}
