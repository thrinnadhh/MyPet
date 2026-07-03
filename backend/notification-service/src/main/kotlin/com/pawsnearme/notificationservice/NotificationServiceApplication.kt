package com.pawsnearme.notificationservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EntityScan(basePackages = ["com.pawsnearme.notificationservice", "com.pawsnearme.common"])
@EnableJpaRepositories(basePackages = ["com.pawsnearme.notificationservice", "com.pawsnearme.common"])
@ComponentScan(basePackages = ["com.pawsnearme.notificationservice", "com.pawsnearme.common"])
class NotificationServiceApplication

fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}
