package com.pawsnearme.contentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ContentServiceApplication

fun main(args: Array<String>) {
    runApplication<ContentServiceApplication>(*args)
}
