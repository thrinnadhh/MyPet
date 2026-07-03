package com.pawsnearme.appointmentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = ["com.pawsnearme.appointmentservice", "com.pawsnearme.common"])
@EnableJpaRepositories(basePackages = ["com.pawsnearme.appointmentservice", "com.pawsnearme.common"])
@ComponentScan(basePackages = ["com.pawsnearme.appointmentservice", "com.pawsnearme.common"])
class AppointmentServiceApplication {
    @Bean
    fun restOperations(): RestOperations = RestTemplate()
}

fun main(args: Array<String>) {
    runApplication<AppointmentServiceApplication>(*args)
}
