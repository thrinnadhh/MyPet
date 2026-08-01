package com.pawsnearme.contentservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object ContentBusinessModule : BusinessModuleDescriptor {
    override val id = "content"
    override val displayName = "Content"
    override val basePackage = "com.pawsnearme.contentservice"
    override val legacyApplicationClassName = "com.pawsnearme.contentservice.ContentServiceApplication"
}
