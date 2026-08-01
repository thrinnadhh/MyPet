package com.pawsnearme.reviewservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object ReviewModule : BusinessModuleDescriptor {
    override val id = "review"
    override val displayName = "Review"
    override val basePackage = "com.pawsnearme.reviewservice"
    override val legacyApplicationClassName = "com.pawsnearme.reviewservice.ReviewServiceApplication"
}
