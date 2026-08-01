package com.pawsnearme.application.module

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.OrderModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component

/** Immutable inventory of cross-module capabilities introduced by M5. */
@Component
class ModuleInterfaceCatalog {
    val contracts: List<ModuleInterfaceDescriptor> = listOf(
        ModuleInterfaceDescriptor("catalog", CatalogModuleApi::class.java.name),
        ModuleInterfaceDescriptor("discovery", DiscoveryModuleApi::class.java.name),
        ModuleInterfaceDescriptor("order", OrderModuleApi::class.java.name),
        ModuleInterfaceDescriptor("payment", PaymentModuleApi::class.java.name),
        ModuleInterfaceDescriptor("provider", ProviderModuleApi::class.java.name)
    ).sortedBy(ModuleInterfaceDescriptor::moduleId)

    init {
        require(contracts.map(ModuleInterfaceDescriptor::moduleId).distinct().size == contracts.size) {
            "Typed module contract ids must be unique"
        }
    }
}

data class ModuleInterfaceDescriptor(
    val moduleId: String,
    val contractClass: String
)

@Component
class ModuleInterfaceInfoContributor(
    private val catalog: ModuleInterfaceCatalog
) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "moduleInterfaces",
            mapOf(
                "count" to catalog.contracts.size,
                "modules" to catalog.contracts.map(ModuleInterfaceDescriptor::moduleId),
                "binding" to "direct-when-present",
                "fallback" to "conditional-http-adapter",
                "transportKnowledgeInBusinessServices" to false
            )
        )
    }
}
