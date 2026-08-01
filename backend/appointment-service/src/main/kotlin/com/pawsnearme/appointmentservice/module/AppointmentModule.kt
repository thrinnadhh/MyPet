package com.pawsnearme.appointmentservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object AppointmentModule : BusinessModuleDescriptor {
    override val id = "appointment"
    override val displayName = "Appointment"
    override val basePackage = "com.pawsnearme.appointmentservice"
    override val legacyApplicationClassName = "com.pawsnearme.appointmentservice.AppointmentServiceApplication"
}
