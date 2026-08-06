package com.pawsnearme.application.module

import com.pawsnearme.appointmentservice.module.AppointmentModule
import com.pawsnearme.captainservice.module.CaptainModule
import com.pawsnearme.catalogservice.module.CatalogModule
import com.pawsnearme.chatservice.module.ChatModule
import com.pawsnearme.common.module.BusinessModuleDescriptor
import com.pawsnearme.contentservice.module.ContentBusinessModule
import com.pawsnearme.discoveryservice.module.DiscoveryModule
import com.pawsnearme.dispatchservice.module.DispatchModule
import com.pawsnearme.notificationservice.module.NotificationModule
import com.pawsnearme.orderservice.module.OrderModule
import com.pawsnearme.paymentservice.module.PaymentModule
import com.pawsnearme.providerservice.module.ProviderModule
import com.pawsnearme.reviewservice.module.ReviewModule
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

class BusinessModuleCatalog(descriptors: List<BusinessModuleDescriptor>) {
    val modules: List<BusinessModuleDescriptor> = descriptors.sortedBy(BusinessModuleDescriptor::id)

    init {
        require(modules.isNotEmpty()) { "At least one business module must be linked" }
        require(modules.map(BusinessModuleDescriptor::id).distinct().size == modules.size) {
            "Business module ids must be unique"
        }
        require(modules.map(BusinessModuleDescriptor::basePackage).distinct().size == modules.size) {
            "Business module base packages must be unique"
        }
        require(modules.all { it.id.matches(Regex("[a-z][a-z0-9-]*")) }) {
            "Business module ids must be stable lowercase identifiers"
        }
    }
}

@Configuration(proxyBeanMethods = false)
class BusinessModuleCatalogConfiguration {
    @Bean
    fun businessModuleCatalog(): BusinessModuleCatalog = BusinessModuleCatalog(
        listOf(
            ProviderModule,
            CatalogModule,
            DiscoveryModule,
            OrderModule,
            AppointmentModule,
            DispatchModule,
            CaptainModule,
            NotificationModule,
            ReviewModule,
            PaymentModule,
            ChatModule,
            ContentBusinessModule
        )
    )
}

@Component
class BusinessModuleInfoContributor(
    private val catalog: BusinessModuleCatalog,
    private val environment: Environment
) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        val modulesEnabled = environment.getProperty(
            "mypet.runtime.modules-enabled",
            Boolean::class.java,
            false
        )
        builder.withDetail(
            "businessModules",
            mapOf(
                "count" to catalog.modules.size,
                "ids" to catalog.modules.map(BusinessModuleDescriptor::id),
                "runtimeMode" to if (modulesEnabled) "active-in-process" else "linked-dormant"
            )
        )
    }
}
