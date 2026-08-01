package com.pawsnearme.application.module

import com.pawsnearme.common.module.BusinessModuleDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusinessModuleCatalogTest {

    @Autowired
    private lateinit var catalog: BusinessModuleCatalog

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `all business modules are linked as runtime libraries`() {
        assertEquals(12, catalog.modules.size)
        assertEquals(
            setOf(
                "appointment",
                "captain",
                "catalog",
                "chat",
                "content",
                "discovery",
                "dispatch",
                "notification",
                "order",
                "payment",
                "provider",
                "review"
            ),
            catalog.modules.map(BusinessModuleDescriptor::id).toSet()
        )

        val classLoader = javaClass.classLoader
        catalog.modules.forEach { module ->
            assertNotNull(
                classLoader.getResource(module.legacyApplicationClassResource),
                "${module.id} module jar is missing ${module.legacyApplicationClassResource}"
            )
        }
    }

    @Test
    fun `legacy service boot applications remain dormant`() {
        val bootApplicationBeans = applicationContext.getBeansWithAnnotation(
            SpringBootApplication::class.java
        )

        assertEquals(1, bootApplicationBeans.size)
        assertTrue(bootApplicationBeans.containsKey("myPetApplication"))
    }

    @Test
    fun `actuator info reports linked dormant modules`() {
        val response = restTemplate.getForEntity("/actuator/info", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val moduleInfo = response.body?.get("businessModules") as? Map<*, *>
        assertNotNull(moduleInfo)
        assertEquals(12, moduleInfo?.get("count"))
        assertEquals("linked-dormant", moduleInfo?.get("runtimeMode"))
        assertEquals(12, (moduleInfo?.get("ids") as? List<*>)?.size)
    }
}
