package com.pawsnearme.orderservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object OrderModule : BusinessModuleDescriptor {
    override val id = "order"
    override val displayName = "Order"
    override val basePackage = "com.pawsnearme.orderservice"
    override val legacyApplicationClassName = "com.pawsnearme.orderservice.OrderServiceApplication"
}
