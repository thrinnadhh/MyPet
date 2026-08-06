package com.pawsnearme.application.edge

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

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
        val signedMedicalDocument = PathPatternRequestMatcher.withDefaults().matcher(
            HttpMethod.GET,
            "/api/v1/appointments/medical-documents/{documentId}/content",
        )
        http
            .securityMatcher(signedMedicalDocument)
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
