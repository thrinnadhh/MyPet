package com.pawsnearme.providerservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object ProviderModule : BusinessModuleDescriptor {
    override val id = "provider"
    override val displayName = "Provider"
    override val basePackage = "com.pawsnearme.providerservice"
    override val legacyApplicationClassName = "com.pawsnearme.providerservice.ProviderServiceApplication"
}
