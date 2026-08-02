package com.pawsnearme.application.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SchedulerArchitectureTest {
    private val backendRoot: Path = Path.of(System.getProperty("mypet.backendRoot"))
    private val repositoryRoot: Path = backendRoot.parent

    private val schedulerOwners = listOf(
        "provider-service",
        "order-service",
        "appointment-service",
        "dispatch-service",
        "notification-service",
        "review-service",
        "payment-service",
        "content-service"
    )

    @Test
    fun `every scheduled method has an explicit distributed lock`() {
        val sources = Files.walk(backendRoot).use { paths ->
            paths.filter { path ->
                path.toString().contains("src/main/kotlin") && path.toString().endsWith(".kt")
            }.map(Files::readString).toList()
        }

        val scheduledCount = sources.sumOf { Regex("@Scheduled\\s*\\(").findAll(it).count() }
        val lockCount = sources.sumOf { Regex("@SchedulerLock\\s*\\(").findAll(it).count() }

        assertEquals(10, scheduledCount)
        assertEquals(scheduledCount, lockCount)
    }

    @Test
    fun `all scheduler owners use shared runtime and lock factory`() {
        schedulerOwners.forEach { module ->
            val packageName = module.replace("-service", "service")
            val source = readBackend(
                "$module/src/main/kotlin/com/pawsnearme/$packageName/config/ShedLockConfig.kt"
            )
            assertTrue(source.contains("SchedulerRuntimeInfrastructureConfiguration"), "$module must install role control")
            assertTrue(source.contains("SchedulerLockProviderFactory.create"), "$module must use the shared lock factory")
            assertFalse(source.contains("JdbcTemplateLockProvider("), "$module must not duplicate provider construction")
        }
    }

    @Test
    fun `all scheduler owners provide API and worker profiles`() {
        schedulerOwners.forEach { module ->
            val api = readBackend("$module/src/main/resources/application-api.yml")
            val worker = readBackend("$module/src/main/resources/application-worker.yml")
            assertTrue(api.contains("MYPET_SCHEDULING_ROLE:API"), "$module API profile must disable workers")
            assertTrue(worker.contains("MYPET_SCHEDULING_ROLE:WORKER"), "$module worker profile must own jobs")
            assertTrue(worker.contains("WORKER_SERVER_PORT:0"), "$module worker must avoid a fixed API port")
        }
    }

    @Test
    fun `optional compose split and launcher cover all scheduler owners`() {
        val overlay = Files.readString(repositoryRoot.resolve("infra/docker-compose.m7.yml"))
        val launcher = Files.readString(repositoryRoot.resolve("scripts/start-m7-workers.sh"))

        schedulerOwners.forEach { module ->
            assertTrue(overlay.contains("$module:"), "M7 overlay must configure $module")
            assertTrue(launcher.contains(module), "M7 launcher must start $module worker")
        }
        assertEquals(8, Regex("MYPET_SCHEDULING_ROLE: API").findAll(overlay).count())
        assertTrue(launcher.contains("SPRING_PROFILES_ACTIVE=docker,worker"))
        assertTrue(launcher.contains("--no-deps"))
        assertFalse(launcher.contains("--service-ports"))
    }

    @Test
    fun `appointment cleanup is included in ShedLock ownership`() {
        val source = readBackend(
            "appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt"
        )
        val scheduledIndex = source.indexOf("@Scheduled(fixedDelay = 5000)")
        val lockIndex = source.indexOf("appointment_cleanupExpiredHolds")
        val methodIndex = source.indexOf("fun cleanupExpiredHolds()")
        assertTrue(scheduledIndex >= 0)
        assertTrue(lockIndex in scheduledIndex until methodIndex)
    }

    private fun readBackend(relativePath: String): String = Files.readString(backendRoot.resolve(relativePath))
}
