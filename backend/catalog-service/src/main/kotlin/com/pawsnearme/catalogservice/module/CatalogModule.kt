package com.pawsnearme.catalogservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object CatalogModule : BusinessModuleDescriptor {
    override val id = "catalog"
    override val displayName = "Catalog"
    override val basePackage = "com.pawsnearme.catalogservice"
    override val legacyApplicationClassName = "com.pawsnearme.catalogservice.CatalogServiceApplication"
}
