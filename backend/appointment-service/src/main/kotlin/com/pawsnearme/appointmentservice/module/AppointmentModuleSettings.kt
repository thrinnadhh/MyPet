package com.pawsnearme.appointmentservice.module

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AppointmentModuleSettings {
    @Bean
    fun appointmentHoldDurationSeconds(
        @Value("\${appointment.hold-duration-seconds:300}") seconds: Long
    ): Long = seconds
}
