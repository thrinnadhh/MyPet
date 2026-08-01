package com.pawsnearme.application.modules

import com.pawsnearme.appointmentservice.AppointmentModuleMarker
import com.pawsnearme.captainservice.CaptainModuleMarker
import com.pawsnearme.catalogservice.CatalogModuleMarker
import com.pawsnearme.chatservice.ChatModuleMarker
import com.pawsnearme.contentservice.ContentModuleMarker
import com.pawsnearme.discoveryservice.DiscoveryModuleMarker
import com.pawsnearme.dispatchservice.DispatchModuleMarker
import com.pawsnearme.notificationservice.NotificationModuleMarker
import com.pawsnearme.orderservice.OrderModuleMarker
import com.pawsnearme.paymentservice.PaymentModuleMarker
import com.pawsnearme.providerservice.ProviderModuleMarker
import com.pawsnearme.reviewservice.ReviewModuleMarker
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

data class InternalModuleDescriptor(
    val id: String,
    val basePackage: String,
    val ownedSchema: String?,
    val marker: KClass<*>,
    val standaloneApplicationClass: String
)

object InternalModuleRegistry {
    val modules: List<InternalModuleDescriptor> = listOf(
        InternalModuleDescriptor(
            "provider",
            "com.pawsnearme.providerservice",
            "providers",
            ProviderModuleMarker::class,
            "com.pawsnearme.providerservice.ProviderServiceApplication"
        ),
        InternalModuleDescriptor(
            "catalog",
            "com.pawsnearme.catalogservice",
            "catalog",
            CatalogModuleMarker::class,
            "com.pawsnearme.catalogservice.CatalogServiceApplication"
        ),
        InternalModuleDescriptor(
            "discovery",
            "com.pawsnearme.discoveryservice",
            null,
            DiscoveryModuleMarker::class,
            "com.pawsnearme.discoveryservice.DiscoveryServiceApplication"
        ),
        InternalModuleDescriptor(
            "order",
            "com.pawsnearme.orderservice",
            "orders",
            OrderModuleMarker::class,
            "com.pawsnearme.orderservice.OrderServiceApplication"
        ),
        InternalModuleDescriptor(
            "appointment",
            "com.pawsnearme.appointmentservice",
            "appointments",
            AppointmentModuleMarker::class,
            "com.pawsnearme.appointmentservice.AppointmentServiceApplication"
        ),
        InternalModuleDescriptor(
            "dispatch",
            "com.pawsnearme.dispatchservice",
            "dispatch",
            DispatchModuleMarker::class,
            "com.pawsnearme.dispatchservice.DispatchServiceApplication"
        ),
        InternalModuleDescriptor(
            "captain",
            "com.pawsnearme.captainservice",
            "captains",
            CaptainModuleMarker::class,
            "com.pawsnearme.captainservice.CaptainServiceApplication"
        ),
        InternalModuleDescriptor(
            "notification",
            "com.pawsnearme.notificationservice",
            "notifications",
            NotificationModuleMarker::class,
            "com.pawsnearme.notificationservice.NotificationServiceApplication"
        ),
        InternalModuleDescriptor(
            "review",
            "com.pawsnearme.reviewservice",
            "reviews",
            ReviewModuleMarker::class,
            "com.pawsnearme.reviewservice.ReviewServiceApplication"
        ),
        InternalModuleDescriptor(
            "payment",
            "com.pawsnearme.paymentservice",
            "payments",
            PaymentModuleMarker::class,
            "com.pawsnearme.paymentservice.PaymentServiceApplication"
        ),
        InternalModuleDescriptor(
            "chat",
            "com.pawsnearme.chatservice",
            "chat",
            ChatModuleMarker::class,
            "com.pawsnearme.chatservice.ChatServiceApplication"
        ),
        InternalModuleDescriptor(
            "content",
            "com.pawsnearme.contentservice",
            "content",
            ContentModuleMarker::class,
            "com.pawsnearme.contentservice.ContentServiceApplication"
        )
    )

    init {
        require(modules.map { it.id }.distinct().size == modules.size) {
            "Internal module IDs must be unique"
        }
        require(modules.map { it.basePackage }.distinct().size == modules.size) {
            "Internal module base packages must be unique"
        }
    }
}

@Component
class InternalModuleInfoContributor : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "internalModules",
            InternalModuleRegistry.modules.map { module ->
                mapOf(
                    "id" to module.id,
                    "basePackage" to module.basePackage,
                    "ownedSchema" to module.ownedSchema
                )
            }
        )
    }
}
