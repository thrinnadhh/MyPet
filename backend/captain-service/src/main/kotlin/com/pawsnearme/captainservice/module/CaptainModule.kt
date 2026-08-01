package com.pawsnearme.captainservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object CaptainModule : BusinessModuleDescriptor {
    override val id = "captain"
    override val displayName = "Captain"
    override val basePackage = "com.pawsnearme.captainservice"
    override val legacyApplicationClassName = "com.pawsnearme.captainservice.CaptainServiceApplication"
}
