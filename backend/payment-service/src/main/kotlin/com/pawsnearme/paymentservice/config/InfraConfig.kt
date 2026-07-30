package com.pawsnearme.paymentservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

@Configuration
class InfraConfig {

    /**
     * RestTemplate with explicit timeouts to prevent thread-pool exhaustion
     * when downstream services (Razorpay, order-service) are slow or unavailable.
     */
    @Bean
    fun restOperations(): RestOperations {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3_000)  // 3 s connection timeout
            setReadTimeout(10_000)    // 10 s read timeout
        }
        return RestTemplate(factory)
    }

    /**
     * Centrally configured ObjectMapper:
     * - JavaTimeModule for Instant/LocalDate serialisation
     * - Kotlin module for data-class support
     * - Dates serialised as ISO strings, not epoch timestamps
     */
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
