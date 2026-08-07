package com.pawsnearme.application.edge

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * The document endpoint is public only because MedicalDocumentService validates
 * a short-lived HMAC token bound to document, actor, expiry and disposition.
 * Reservation, upload, list and signed-link creation remain authenticated.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mypet.edge", name = ["enabled"], havingValue = "true")
class MedicalDocumentEdgeSecurityConfiguration {
    @Bean
    @Order(-1)
    fun medicalDocumentEdgeSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(SignedContentRequestMatchers.medicalDocument)
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
