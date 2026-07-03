package com.pawsnearme.chatservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EntityScan(basePackages = ["com.pawsnearme.chatservice", "com.pawsnearme.common"])
@EnableJpaRepositories(basePackages = ["com.pawsnearme.chatservice", "com.pawsnearme.common"])
@ComponentScan(basePackages = ["com.pawsnearme.chatservice", "com.pawsnearme.common"])
class ChatServiceApplication

fun main(args: Array<String>) {
    runApplication<ChatServiceApplication>(*args)
}
