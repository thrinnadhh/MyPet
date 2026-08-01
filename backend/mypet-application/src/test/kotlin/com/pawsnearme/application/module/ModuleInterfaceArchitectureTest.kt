package com.pawsnearme.application.module

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.OrderModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.ProviderModuleApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ModuleInterfaceArchitectureTest {
    @Test
    fun `application exposes the complete typed module contract catalog`() {
        val catalog = ModuleInterfaceCatalog()
        assertEquals(
            listOf("catalog", "discovery", "order", "payment", "provider"),
            catalog.contracts.map(ModuleInterfaceDescriptor::moduleId)
        )
        listOf(
            CatalogModuleApi::class.java,
            DiscoveryModuleApi::class.java,
            OrderModuleApi::class.java,
            PaymentModuleApi::class.java,
            ProviderModuleApi::class.java
        ).forEach(::assertNotNull)
    }

    @Test
    fun `migrated business services contain no HTTP route execution`() {
        val backendRoot = Path.of(requireNotNull(System.getProperty("mypet.backendRoot")))
        val migratedServices = listOf(
            "order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt",
            "order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderCompensationService.kt",
            "appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt",
            "dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt",
            "content-service/src/main/kotlin/com/pawsnearme/contentservice/service/BannerAuctionService.kt",
            "notification-service/src/main/kotlin/com/pawsnearme/notificationservice/service/VaccinationReminderSyncWorker.kt"
        )
        val forbidden = listOf(
            "/api/v1/",
            ".exchange(",
            ".postForEntity(",
            ".getForObject(",
            "UriComponentsBuilder"
        )
        migratedServices.forEach { relative ->
            val source = backendRoot.resolve(relative).readText()
            forbidden.forEach { token ->
                assertFalse(source.contains(token), "$relative must not contain transport token $token")
            }
        }
    }
}
