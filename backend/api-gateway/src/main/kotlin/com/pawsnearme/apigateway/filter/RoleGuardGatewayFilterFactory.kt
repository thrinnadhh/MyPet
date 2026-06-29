package com.pawsnearme.apigateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * RoleGuardFilter — rejects requests whose X-User-Role header does not match
 * any of the allowed roles declared in the route config.
 *
 * Usage in application.yml:
 *   filters:
 *     - name: RoleGuard
 *       args:
 *         roles: MERCHANT,ADMIN
 */
@Component
class RoleGuardGatewayFilterFactory :
    AbstractGatewayFilterFactory<RoleGuardGatewayFilterFactory.Config>(Config::class.java) {

    data class Config(var roles: String = "")

    override fun apply(config: Config): GatewayFilter {
        val allowedRoles = config.roles
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toSet()

        return GatewayFilter { exchange, chain ->
            val role = exchange.request.headers.getFirst("X-User-Role")?.uppercase() ?: ""
            if (role in allowedRoles) {
                chain.filter(exchange)
            } else {
                forbidden(exchange)
            }
        }
    }

    private fun forbidden(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.add("Content-Type", "application/json")
        val body = """{"error":"Access denied: insufficient role"}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray())
        return response.writeWith(Mono.just(buffer))
    }

    override fun name(): String = "RoleGuard"
}
