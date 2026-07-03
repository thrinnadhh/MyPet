package com.pawsnearme.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Enforces that every inbound HTTP request to a downstream microservice
 * was forwarded by the API Gateway.
 *
 * The gateway must add the `X-Internal-Gateway-Secret` header with the value
 * of the `GATEWAY_SECRET` environment variable to every proxied request.
 *
 * This filter is **opt-in**: it only activates when
 * `gateway.trust.enabled=true` is set in application.yml/env.
 * Services can leave it off in local dev and enable it in staging/production.
 *
 * Example application.yml:
 *   gateway:
 *     trust:
 *       enabled: true
 *       secret: ${GATEWAY_SECRET}
 */
@Component
@ConditionalOnProperty(name = ["gateway.trust.enabled"], havingValue = "true")
class GatewayTrustFilter(private val props: GatewayTrustProperties) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(GatewayTrustFilter::class.java)

    companion object {
        const val HEADER = "X-Internal-Gateway-Secret"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val incoming = request.getHeader(HEADER)
        if (incoming.isNullOrBlank() || incoming != props.secret) {
            log.warn(
                "Rejected request to {} {} — missing or invalid gateway secret",
                request.method,
                request.requestURI
            )
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = "application/json"
            response.writer.write("""{"error":"Direct access to this service is not permitted"}""")
            return
        }
        filterChain.doFilter(request, response)
    }
}

/**
 * Configuration properties for the gateway trust filter.
 * Bound from `gateway.trust.*` in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "gateway.trust")
class GatewayTrustProperties {
    var enabled: Boolean = false
    var secret: String = ""
}
