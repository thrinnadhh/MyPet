package com.pawsnearme.apigateway.filter

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Adds the gateway-only trust credential after route/default request-header
 * sanitizers have removed any client-supplied value.
 *
 * Supplying GATEWAY_SECRET activates the trust boundary. This matches the
 * deployment contract, where Compose/Kubernetes require that secret. A local
 * gateway without the environment variable remains usable alongside services
 * that have gateway trust disabled.
 */
@Component
class GatewayTrustHeaderFilter(
    @Value("\${GATEWAY_SECRET:}") private val trustSecret: String
) : GlobalFilter, Ordered {

    init {
        if (trustSecret.isNotBlank()) {
            require(trustSecret != DEVELOPMENT_SECRET) {
                "The development gateway trust secret cannot be used for downstream trust"
            }
        }
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        if (trustSecret.isBlank()) {
            return chain.filter(exchange)
        }

        val trustedRequest = exchange.request.mutate()
            .headers { headers ->
                // set() replaces any value that survived an earlier sanitizer.
                headers.set(SECRET_HEADER, trustSecret)
            }
            .build()

        return chain.filter(exchange.mutate().request(trustedRequest).build())
    }

    /**
     * Route/default filters normally run near order 0. Injecting late ensures
     * RemoveRequestHeader sanitization has already happened, while still
     * running before the Netty routing filter sends the downstream request.
     */
    override fun getOrder(): Int = TRUST_INJECTION_ORDER

    companion object {
        const val SECRET_HEADER = "X-Internal-Gateway-Secret"
        private const val DEVELOPMENT_SECRET = "dev-secret-change-in-production"
        private const val TRUST_INJECTION_ORDER = 10_000
    }
}
