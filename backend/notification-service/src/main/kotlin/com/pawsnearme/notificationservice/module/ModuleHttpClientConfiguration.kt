package com.pawsnearme.notificationservice.module

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

@Configuration(proxyBeanMethods = false)
class ModuleHttpClientConfiguration {
    @Bean
    @ConditionalOnMissingBean(RestOperations::class)
    fun moduleRestOperations(): RestOperations = RestTemplate()
}
