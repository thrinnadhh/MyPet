package com.pawsnearme.paymentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EntityScan(
    basePackages = [
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.common.idempotency",
        "com.pawsnearme.common.outbox"
    ]
)
@EnableJpaRepositories(
    basePackages = [
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.common.idempotency",
        "com.pawsnearme.common.outbox"
    ]
)
@ComponentScan(
    basePackages = [
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.common.security",
        "com.pawsnearme.common.idempotency",
        "com.pawsnearme.common.outbox",
        "com.pawsnearme.common.scheduling"
    ]
)
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}
