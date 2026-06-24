package com.pawsnearme.apigateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class AuthenticationHeaderFilter : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication }
            .flatMap { authentication ->
                val principal = authentication.principal
                if (principal is Jwt) {
                    val userId = principal.subject // 'sub' claim
                    
                    // Extract role from standard claims, 'role', or nested claims in 'app_metadata'
                    val role = extractRole(principal)
                    
                    val mutatedRequest = exchange.request.mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .build()
                    
                    Mono.just(exchange.mutate().request(mutatedRequest).build())
                } else {
                    Mono.just(exchange)
                }
            }
            .defaultIfEmpty(exchange)
            .flatMap { chain.filter(it) }
    }

    private fun extractRole(jwt: Jwt): String {
        // Try direct 'role' claim first
        val directRole = jwt.claims["role"] as? String
        if (!directRole.isNullOrBlank()) return directRole.uppercase()

        // Try nested 'app_metadata' -> 'role'
        val appMetadata = jwt.claims["app_metadata"] as? Map<*, *>
        val nestedAppRole = appMetadata?.get("role") as? String
        if (!nestedAppRole.isNullOrBlank()) return nestedAppRole.uppercase()

        // Try nested 'user_metadata' -> 'role'
        val userMetadata = jwt.claims["user_metadata"] as? Map<*, *>
        val nestedUserRole = userMetadata?.get("role") as? String
        if (!nestedUserRole.isNullOrBlank()) return nestedUserRole.uppercase()

        return "CUSTOMER" // Default role
    }

    override fun getOrder(): Int {
        // Run after security filter completes
        return Ordered.LOWEST_PRECEDENCE - 100
    }
}
