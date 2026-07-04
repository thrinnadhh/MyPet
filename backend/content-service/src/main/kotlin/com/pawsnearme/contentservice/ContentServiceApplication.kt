package com.pawsnearme.contentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

@SpringBootApplication
@EnableScheduling
class ContentServiceApplication {
    @Bean
    fun restOperations(): RestOperations = RestTemplate()
}

fun main(args: Array<String>) {
    runApplication<ContentServiceApplication>(*args)
}
