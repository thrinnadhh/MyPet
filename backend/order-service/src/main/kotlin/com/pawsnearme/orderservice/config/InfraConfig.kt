package com.pawsnearme.orderservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class InfraConfig {

    /**
     * RestTemplate with explicit timeouts.
     * Without timeouts a slow catalog or payment service blocks the entire
     * thread pool and causes a cascading service outage.
     */
    @Bean
    fun timedRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3_000)  // 3 s — fail fast if service is unreachable
            setReadTimeout(10_000)    // 10 s — match Resilience4j TimeLimiter budget
        }
        return RestTemplate(factory)
    }

    /**
     * Centrally configured ObjectMapper shared across order-service.
     * JavaTimeModule ensures Instant fields serialise as ISO strings, not epoch numbers.
     * Spring Boot's autoconfigured ObjectMapper is the primary bean; this is a
     * named qualifier used by injection points that need explicit JavaTimeModule.
     */
    @Bean
    fun orderObjectMapper(): ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

