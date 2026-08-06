package com.pawsnearme.application.module

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class M2ModuleBoundaryTest {

    private data class ModuleSource(
        val projectName: String,
        val basePackage: String
    )

    private val modules = listOf(
        ModuleSource("provider-service", "com.pawsnearme.providerservice"),
        ModuleSource("catalog-service", "com.pawsnearme.catalogservice"),
        ModuleSource("discovery-service", "com.pawsnearme.discoveryservice"),
        ModuleSource("order-service", "com.pawsnearme.orderservice"),
        ModuleSource("appointment-service", "com.pawsnearme.appointmentservice"),
        ModuleSource("dispatch-service", "com.pawsnearme.dispatchservice"),
        ModuleSource("captain-service", "com.pawsnearme.captainservice"),
        ModuleSource("notification-service", "com.pawsnearme.notificationservice"),
        ModuleSource("review-service", "com.pawsnearme.reviewservice"),
        ModuleSource("payment-service", "com.pawsnearme.paymentservice"),
        ModuleSource("chat-service", "com.pawsnearme.chatservice"),
        ModuleSource("content-service", "com.pawsnearme.contentservice")
    )

    private val backendRoot: Path = Path.of(
        requireNotNull(System.getProperty("mypet.backendRoot")) {
            "mypet.backendRoot test system property is required"
        }
    ).toAbsolutePath().normalize()

    @Test
    fun `business service builds do not depend directly on another business service`() {
        val businessProjectNames = modules.map(ModuleSource::projectName).toSet()
        val projectDependency = Regex("""project\(\s*\":([^\"]+)\"\s*\)""")

        modules.forEach { owner ->
            val buildFile = backendRoot.resolve(owner.projectName).resolve("build.gradle.kts")
            val dependencies = projectDependency.findAll(Files.readString(buildFile))
                .map { it.groupValues[1] }
                .filter { it in businessProjectNames }
                .toSet()

            assertTrue(
                dependencies.isEmpty(),
                "${owner.projectName} must communicate through module APIs, not Gradle dependencies: $dependencies"
            )
        }
    }

    @Test
    fun `business modules do not access another module repository package`() {
        modules.forEach { owner ->
            val sourceRoot = backendRoot
                .resolve(owner.projectName)
                .resolve("src/main/kotlin")
            val forbiddenRepositoryPackages = modules
                .filterNot { it == owner }
                .map { "${it.basePackage}.repository" }

            Files.walk(sourceRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .forEach { sourceFile ->
                        val source = Files.readString(sourceFile)
                        val violations = forbiddenRepositoryPackages.filter(source::contains)

                        assertTrue(
                            violations.isEmpty(),
                            "${backendRoot.relativize(sourceFile)} accesses another module repository: $violations"
                        )
                    }
            }
        }
    }

    @Test
    fun `consolidated application packages every module runtime transitively`() {
        val build = Files.readString(
            backendRoot.resolve("mypet-application").resolve("build.gradle.kts")
        )

        modules.forEach { module ->
            val activeDeclaration =
                "implementation(project(\":${module.projectName}\"))"
            assertTrue(
                activeDeclaration in build,
                "${module.projectName} must be packaged into the monolith runtime"
            )
        }
        assertFalse(
            "isTransitive = false" in build,
            "M9 must not package business modules as dormant non-transitive libraries"
        )
    }
}
