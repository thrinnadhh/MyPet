package com.pawsnearme.apigateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LoyaltyRouteConfig(
    @Value("\${PAYMENT_SERVICE_URL:http://localhost:8090}")
    private val paymentServiceUrl: String
) {
    @Bean
    fun loyaltyRouteLocator(builder: RouteLocatorBuilder): RouteLocator =
        builder.routes()
            .route("loyalty-service") { route ->
                route.path("/api/v1/loyalty/**")
                    .uri(paymentServiceUrl)
            }
            .build()
}
