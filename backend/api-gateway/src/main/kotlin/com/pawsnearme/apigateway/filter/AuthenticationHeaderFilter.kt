package com.pawsnearme.apigateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class AuthenticationHeaderFilter(
    private val redisTemplate: ReactiveStringRedisTemplate? = null
) : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val sanitizedExchange = exchange.mutate()
            .request(
                exchange.request.mutate()
                    .headers {
                        it.remove("X-User-Id")
                        it.remove("X-User-Role")
                        it.remove("X-User-Email")
                        it.remove("X-User-Full-Name")
                        it.remove("X-User-Phone")
                        it.remove("X-Admin-Api-Key")
                        it.remove("X-Internal-Gateway-Secret")
                        it.remove("X-Internal-Secret")
                        it.remove("X-Service-Name")
                    }
                    .build()
            )
            .build()

        val authenticationMono = exchange.getPrincipal<Authentication>()
            .switchIfEmpty(ReactiveSecurityContextHolder.getContext().map { it.authentication })
            .filter { it.isAuthenticated }
            .cache()

        return authenticationMono
            .flatMap { authentication ->
                val principal = authentication.principal
                if (principal is Jwt) {
                    val userId = principal.subject
                    val role = extractRole(principal)
                    val email = extractStringClaim(principal, "email")
                    val fullName = extractFullName(principal)
                    val phone = extractPhone(principal)

                    val hasKeyMono = redisTemplate?.hasKey("suspended_user:$userId") ?: Mono.just(false)
                    hasKeyMono.flatMap { isSuspended ->
                        if (isSuspended) {
                            forbidden(sanitizedExchange, "User access has been revoked")
                        } else {
                            val requestBuilder = sanitizedExchange.request.mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Role", role)
                            if (!email.isNullOrBlank()) requestBuilder.header("X-User-Email", email)
                            if (!fullName.isNullOrBlank()) requestBuilder.header("X-User-Full-Name", fullName)
                            if (!phone.isNullOrBlank()) requestBuilder.header("X-User-Phone", phone)

                            val mutatedRequest = requestBuilder.build()
                            val newExchange = sanitizedExchange.mutate().request(mutatedRequest).build()
                            chain.filter(newExchange)
                        }
                    }
                } else {
                    chain.filter(sanitizedExchange)
                }
            }
            .then(
                authenticationMono.hasElement().flatMap { hasAuthentication ->
                    if (hasAuthentication) Mono.empty() else chain.filter(sanitizedExchange)
                }
            )
    }

    private fun forbidden(exchange: ServerWebExchange, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.add("Content-Type", "application/json")
        val body = """{"error":"$message"}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray())
        return response.writeWith(Mono.just(buffer))
    }

    private fun extractRole(jwt: Jwt): String {
        // Supabase's direct `role` claim is usually the platform role
        // (`authenticated`/`anon`). Prefer app_metadata for app authorization.
        val appMetadata = jwt.claims["app_metadata"] as? Map<*, *>
        val nestedAppRole = appMetadata?.get("role") as? String
        if (!nestedAppRole.isNullOrBlank()) return normalizeRole(nestedAppRole)


        val directRole = jwt.claims["role"] as? String
        if (!directRole.isNullOrBlank()) {
            val normalized = normalizeRole(directRole)
            if (normalized !in setOf("AUTHENTICATED", "ANON", "SERVICE_ROLE")) {
                return normalized
            }
        }

        return "CUSTOMER" // Default role
    }

    private fun normalizeRole(role: String): String {
        val normalized = role.uppercase()
        return if (normalized == "PROVIDER") "MERCHANT" else normalized
    }

    private fun extractStringClaim(jwt: Jwt, claim: String): String? {
        return jwt.claims[claim] as? String
    }

    private fun extractFullName(jwt: Jwt): String? {
        val userMetadata = jwt.claims["user_metadata"] as? Map<*, *>
        return (userMetadata?.get("full_name") as? String)
            ?: (userMetadata?.get("name") as? String)
            ?: extractStringClaim(jwt, "name")
    }

    private fun extractPhone(jwt: Jwt): String? {
        val userMetadata = jwt.claims["user_metadata"] as? Map<*, *>
        return (userMetadata?.get("phone") as? String)
            ?: (userMetadata?.get("phone_number") as? String)
            ?: extractStringClaim(jwt, "phone")
            ?: extractStringClaim(jwt, "phone_number")
    }

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }
}
