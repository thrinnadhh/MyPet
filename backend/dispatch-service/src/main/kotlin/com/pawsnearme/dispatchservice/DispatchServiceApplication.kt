package com.pawsnearme.dispatchservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DispatchServiceApplication

fun main(args: Array<String>) {
    runApplication<DispatchServiceApplication>(*args)
}
