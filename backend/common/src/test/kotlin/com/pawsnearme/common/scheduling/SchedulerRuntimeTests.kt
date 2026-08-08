package com.pawsnearme.common.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import javax.sql.DataSource

class SchedulerRuntimeTests {
    @Test
    fun `catalog inventories every scheduler owner and lock identity`() {
        val catalog = MyPetSchedulerCatalog.create()

        assertEquals(15, catalog.jobs.size)
        assertEquals(8, catalog.ownerModules.size)
        assertEquals(13, catalog.jobs.count { it.cadenceKind == SchedulerCadenceKind.FIXED_DELAY })
        assertEquals(2, catalog.jobs.count { it.cadenceKind == SchedulerCadenceKind.CRON })
        assertEquals(catalog.jobs.size, catalog.jobs.map { it.lockIdentity }.distinct().size)
        assertEquals(4, catalog.jobsOwnedBy("order").size)
        assertTrue(
            catalog.jobs.any {
                it.id == "order.recurring-order-generation" &&
                    it.component == "RecurringOrderScheduler" &&
                    it.method == "generateDueOrders" &&
                    it.lockIdentity == "orders.shedlock/recurringOrderGeneration"
            }
        )
    }

    @Test
    fun `runtime roles preserve legacy default and split API from workers`() {
        assertEquals(SchedulerRuntimeRole.ALL, SchedulerRuntimeRole.parse(null))
        assertEquals(SchedulerRuntimeRole.API, SchedulerRuntimeRole.parse("api"))
        assertEquals(SchedulerRuntimeRole.WORKER, SchedulerRuntimeRole.parse("WORKER"))
        assertTrue(SchedulerRuntimeRole.ALL.executesWorkers)
        assertTrue(SchedulerRuntimeRole.WORKER.executesWorkers)
        assertFalse(SchedulerRuntimeRole.API.executesWorkers)
        assertFalse(SchedulerRuntimeRole.DISABLED.executesWorkers)
    }

    @Test
    fun `API runtime removes scheduled annotation processor`() {
        val registry = DefaultListableBeanFactory()
        registry.registerBeanDefinition(
            SchedulerRoleBeanDefinitionPostProcessor.SCHEDULED_PROCESSOR_BEAN_NAME,
            RootBeanDefinition(Any::class.java)
        )

        SchedulerRoleBeanDefinitionPostProcessor(SchedulerRuntimeRole.API)
            .postProcessBeanDefinitionRegistry(registry)

        assertFalse(
            registry.containsBeanDefinition(
                SchedulerRoleBeanDefinitionPostProcessor.SCHEDULED_PROCESSOR_BEAN_NAME
            )
        )
    }

    @Test
    fun `worker runtime retains scheduled annotation processor`() {
        val registry = DefaultListableBeanFactory()
        registry.registerBeanDefinition(
            SchedulerRoleBeanDefinitionPostProcessor.SCHEDULED_PROCESSOR_BEAN_NAME,
            RootBeanDefinition(Any::class.java)
        )

        SchedulerRoleBeanDefinitionPostProcessor(SchedulerRuntimeRole.WORKER)
            .postProcessBeanDefinitionRegistry(registry)

        assertTrue(
            registry.containsBeanDefinition(
                SchedulerRoleBeanDefinitionPostProcessor.SCHEDULED_PROCESSOR_BEAN_NAME
            )
        )
    }

    @Test
    fun `lock provider rejects unsafe table identifiers before JDBC use`() {
        val dataSource = mock<DataSource>()
        assertThrows(IllegalArgumentException::class.java) {
            SchedulerLockProviderFactory.create(dataSource, "orders.shedlock;drop table orders")
        }
    }
}