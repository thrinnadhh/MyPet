package com.pawsnearme.orderservice.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
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
     * KotlinModule provides native support for Kotlin data classes.
     */
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}


