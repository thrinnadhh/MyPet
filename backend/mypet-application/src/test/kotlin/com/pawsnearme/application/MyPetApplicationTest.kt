package com.pawsnearme.application

import com.pawsnearme.application.modules.InternalModuleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.Collections

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyPetApplicationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `application starts and readiness probe reports up`() {
        val response = restTemplate.getForEntity(
            "/actuator/health/readiness",
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("UP", response.body?.get("status"))
    }

    @Test
    fun `all internal module artifacts are packaged and reported`() {
        val modules = InternalModuleRegistry.modules

        assertEquals(12, modules.size)
        assertEquals(12, modules.map { it.id }.distinct().size)
        assertEquals(12, modules.map { it.basePackage }.distinct().size)
        modules.forEach { module ->
            assertEquals(module.basePackage, module.marker.java.packageName)
        }

        val response = restTemplate.getForEntity("/actuator/info", Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        @Suppress("UNCHECKED_CAST")
        val reportedModules = response.body?.get("internalModules") as? List<Map<String, Any?>>
        assertNotNull(reportedModules)
        assertEquals(12, reportedModules?.size)
    }

    @Test
    fun `standalone launchers and service bootstrap resources are excluded`() {
        val classLoader = javaClass.classLoader

        InternalModuleRegistry.modules.forEach { module ->
            assertThrows(ClassNotFoundException::class.java) {
                Class.forName(module.standaloneApplicationClass, false, classLoader)
            }
        }

        val applicationResources = Collections.list(classLoader.getResources("application.yml"))
        assertEquals(
            1,
            applicationResources.size,
            "Only mypet-application may contribute application.yml"
        )

        listOf(
            "db/migration/V1__init_orders.sql",
            "db/migration/V1__init_payments.sql",
            "db/migration/V1__init_content.sql"
        ).forEach { migration ->
            assertNull(
                classLoader.getResource(migration),
                "Service migration leaked into the M2 application artifact: $migration"
            )
        }
    }
}
