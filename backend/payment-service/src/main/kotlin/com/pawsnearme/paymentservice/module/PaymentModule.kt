package com.pawsnearme.paymentservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object PaymentModule : BusinessModuleDescriptor {
    override val id = "payment"
    override val displayName = "Payment and Loyalty"
    override val basePackage = "com.pawsnearme.paymentservice"
    override val legacyApplicationClassName = "com.pawsnearme.paymentservice.PaymentServiceApplication"
}
