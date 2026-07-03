package com.pawsnearme.captainservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.pawsnearme.captainservice", "com.pawsnearme.common.security"])
class CaptainServiceApplication

fun main(args: Array<String>) {
    runApplication<CaptainServiceApplication>(*args)
}
