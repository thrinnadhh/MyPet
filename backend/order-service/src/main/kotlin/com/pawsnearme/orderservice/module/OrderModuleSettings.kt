package com.pawsnearme.orderservice.module

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OrderModuleSettings {
    @Bean
    fun orderOnlinePaymentsEnabled(
        @Value("\${order.online-payments-enabled:false}") enabled: Boolean
    ): Boolean = enabled
}
