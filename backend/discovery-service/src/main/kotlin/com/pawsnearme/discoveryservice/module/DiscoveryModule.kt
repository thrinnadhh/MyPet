package com.pawsnearme.discoveryservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object DiscoveryModule : BusinessModuleDescriptor {
    override val id = "discovery"
    override val displayName = "Discovery"
    override val basePackage = "com.pawsnearme.discoveryservice"
    override val legacyApplicationClassName = "com.pawsnearme.discoveryservice.DiscoveryServiceApplication"
}
