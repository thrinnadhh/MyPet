package com.pawsnearme.appointmentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AppointmentServiceApplication

fun main(args: Array<String>) {
    runApplication<AppointmentServiceApplication>(*args)
}
