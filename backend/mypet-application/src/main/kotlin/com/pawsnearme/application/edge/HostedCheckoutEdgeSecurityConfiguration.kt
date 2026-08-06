package com.pawsnearme.application.edge

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Public browser access is limited to the signed hosted-checkout document.
 * HostedCheckoutService validates the short-lived HMAC token before returning
 * any payment metadata. Session creation, status reads, and order confirmation
 * remain on the authenticated application security chain.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mypet.edge", name = ["enabled"], havingValue = "true")
class HostedCheckoutEdgeSecurityConfiguration {
    @Bean
    @Order(0)
    fun hostedCheckoutEdgeSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(SignedContentRequestMatchers.hostedCheckout)
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
