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
    fun `application exposes cutover and consolidation metadata`() {
        val response = restTemplate.getForEntity("/actuator/info", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val appInfo = response.body?.get("app") as? Map<*, *>
        assertNotNull(appInfo)
        assertEquals("MyPet Application", appInfo?.get("name"))
        assertEquals("M10", appInfo?.get("milestone"))
        assertEquals("modular-monolith", appInfo?.get("architecture"))

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

        val interfaceInfo = response.body?.get("moduleInterfaces") as? Map<*, *>
        assertNotNull(interfaceInfo)
        assertEquals(5, interfaceInfo?.get("count"))
        assertEquals("direct-when-present", interfaceInfo?.get("binding"))
        assertEquals("conditional-http-adapter", interfaceInfo?.get("fallback"))
        assertEquals(false, interfaceInfo?.get("transportKnowledgeInBusinessServices"))

        val workflowInfo = response.body?.get("workflowRuntime") as? Map<*, *>
        assertNotNull(workflowInfo)
        assertEquals("M6", workflowInfo?.get("milestone"))
        assertEquals("KAFKA_ONLY", workflowInfo?.get("deliveryMode"))
        assertEquals(13, workflowInfo?.get("routeCount"))
        assertEquals(true, workflowInfo?.get("kafkaRollbackRetained"))

        val schedulerInfo = response.body?.get("schedulerRuntime") as? Map<*, *>
        assertNotNull(schedulerInfo)
        assertEquals("M7", schedulerInfo?.get("milestone"))
        assertEquals("API", schedulerInfo?.get("role"))
        assertEquals(false, schedulerInfo?.get("workersEnabled"))
        assertEquals(14, schedulerInfo?.get("jobCount"))
        assertEquals(8, schedulerInfo?.get("ownerCount"))
        assertEquals(13, schedulerInfo?.get("fixedDelayJobCount"))
        assertEquals(1, schedulerInfo?.get("cronJobCount"))
        assertEquals("shared-jdbc-db-time", schedulerInfo?.get("lockProvider"))
        assertEquals(true, schedulerInfo?.get("apiWorkerSplitSupported"))
        assertEquals(8, (schedulerInfo?.get("lockTables") as? List<*>)?.size)
        assertEquals(14, (schedulerInfo?.get("jobs") as? List<*>)?.size)

        val verificationInfo = response.body?.get("featureVerification") as? Map<*, *>
        assertNotNull(verificationInfo)
        assertEquals("M10", verificationInfo?.get("milestone"))
        assertEquals("modular-monolith-cutover", verificationInfo?.get("mode"))
        assertEquals(14, verificationInfo?.get("domainCount"))
        assertEquals(true, verificationInfo?.get("cutoverAuthorized"))
        assertEquals(false, verificationInfo?.get("legacyRollbackRequired"))
        assertEquals(true, verificationInfo?.get("legacyRollbackAvailable"))
        assertEquals(14, (verificationInfo?.get("domains") as? List<*>)?.size)
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
