package com.pawsnearme.apigateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers

/**
 * The hosted Razorpay page is opened by the system browser, which cannot inherit
 * the app's bearer token. Access remains protected by the short-lived HMAC token
 * validated inside payment-service; every session-creation and status endpoint
 * continues through the authenticated gateway chain.
 */
@Configuration
class HostedCheckoutSecurityConfig {
    @Bean
    @Order(0)
    fun hostedCheckoutSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .securityMatcher(
                ServerWebExchangeMatchers.pathMatchers(
                    HttpMethod.GET,
                    "/api/v1/payments/checkout/**"
                )
            )
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().permitAll() }
        return http.build()
    }
}
