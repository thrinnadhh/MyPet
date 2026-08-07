package com.pawsnearme.application.edge

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/** Public access is limited to HMAC-signed, short-lived case-evidence content. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mypet.edge", name = ["enabled"], havingValue = "true")
class CustomerCaseEvidenceEdgeSecurityConfiguration {
    @Bean
    @Order(-2)
    fun customerCaseEvidenceEdgeSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(SignedContentRequestMatchers.customerCaseEvidence)
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
