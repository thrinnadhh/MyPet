package com.pawsnearme.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyPetApplicationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `application exposes liveness and readiness probes`() {
        assertHealthEndpoint("/actuator/health/liveness")
        assertHealthEndpoint("/actuator/health/readiness")
    }

    @Test
    fun `application exposes milestone edge and database metadata through info endpoint`() {
        val response = restTemplate.getForEntity("/actuator/info", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val appInfo = response.body?.get("app") as? Map<*, *>
        assertNotNull(appInfo)
        assertEquals("MyPet Application", appInfo?.get("name"))
        assertEquals("M4", appInfo?.get("milestone"))
        assertEquals("modular-monolith-database-consolidation", appInfo?.get("architecture"))

        val edgeInfo = response.body?.get("edgeSecurity") as? Map<*, *>
        assertNotNull(edgeInfo)
        assertEquals(false, edgeInfo?.get("enabled"))
        assertEquals("shadow-ready", edgeInfo?.get("mode"))

        val databaseInfo = response.body?.get("databaseConsolidation") as? Map<*, *>
        assertNotNull(databaseInfo)
        assertEquals(false, databaseInfo?.get("enabled"))
        assertEquals("shadow-ready", databaseInfo?.get("mode"))
        assertEquals("DISABLED", databaseInfo?.get("phase"))
        assertEquals(12, databaseInfo?.get("moduleCount"))
    }

    @Test
    fun `application exposes prometheus scrape endpoint`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    private fun assertHealthEndpoint(path: String) {
        val response = restTemplate.getForEntity(path, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("UP", response.body?.get("status"))
    }
}
