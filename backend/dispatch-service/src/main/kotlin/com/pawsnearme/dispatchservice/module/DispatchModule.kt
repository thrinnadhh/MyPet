package com.pawsnearme.dispatchservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object DispatchModule : BusinessModuleDescriptor {
    override val id = "dispatch"
    override val displayName = "Dispatch"
    override val basePackage = "com.pawsnearme.dispatchservice"
    override val legacyApplicationClassName = "com.pawsnearme.dispatchservice.DispatchServiceApplication"
}
