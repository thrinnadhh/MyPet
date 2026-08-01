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
    fun `application exposes milestone metadata through info endpoint`() {
        val response = restTemplate.getForEntity("/actuator/info", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val appInfo = response.body?.get("app") as? Map<*, *>
        assertNotNull(appInfo)
        assertEquals("MyPet Application", appInfo?.get("name"))
        assertEquals("M1", appInfo?.get("milestone"))
        assertEquals("modular-monolith-shell", appInfo?.get("architecture"))
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
