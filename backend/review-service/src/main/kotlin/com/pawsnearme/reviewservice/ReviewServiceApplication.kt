package com.pawsnearme.reviewservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = ["com.pawsnearme.reviewservice", "com.pawsnearme.common"])
@EnableJpaRepositories(basePackages = ["com.pawsnearme.reviewservice", "com.pawsnearme.common"])
@ComponentScan(basePackages = ["com.pawsnearme.reviewservice", "com.pawsnearme.common"])
class ReviewServiceApplication

fun main(args: Array<String>) {
    runApplication<ReviewServiceApplication>(*args)
}
