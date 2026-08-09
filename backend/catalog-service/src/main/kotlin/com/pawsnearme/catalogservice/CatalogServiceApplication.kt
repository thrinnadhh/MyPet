package com.pawsnearme.catalogservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = ["com.pawsnearme.catalogservice", "com.pawsnearme.common.outbox"])
@EnableJpaRepositories(basePackages = ["com.pawsnearme.catalogservice", "com.pawsnearme.common.outbox"])
@ComponentScan(basePackages = ["com.pawsnearme.catalogservice", "com.pawsnearme.common.outbox"])
class CatalogServiceApplication

fun main(args: Array<String>) {
    runApplication<CatalogServiceApplication>(*args)
}
