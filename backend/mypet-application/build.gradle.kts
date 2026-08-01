import org.gradle.api.file.DuplicatesStrategy
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // M2 links each business module as a dormant library. Transitive runtime
    // dependencies remain disabled until their owning migration milestones
    // explicitly activate persistence, messaging and infrastructure concerns.
    implementation(project(":common")) { isTransitive = false }
    implementation(project(":provider-service")) { isTransitive = false }
    implementation(project(":catalog-service")) { isTransitive = false }
    implementation(project(":discovery-service")) { isTransitive = false }
    implementation(project(":order-service")) { isTransitive = false }
    implementation(project(":appointment-service")) { isTransitive = false }
    implementation(project(":dispatch-service")) { isTransitive = false }
    implementation(project(":captain-service")) { isTransitive = false }
    implementation(project(":notification-service")) { isTransitive = false }
    implementation(project(":review-service")) { isTransitive = false }
    implementation(project(":payment-service")) { isTransitive = false }
    implementation(project(":chat-service")) { isTransitive = false }
    implementation(project(":content-service")) { isTransitive = false }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// M4 packages service-owned migrations under namespaced application paths.
// Original service resources and filenames remain untouched for rollback.
val migrationProjects = linkedMapOf(
    "provider" to ":provider-service",
    "catalog" to ":catalog-service",
    "discovery" to ":discovery-service",
    "order" to ":order-service",
    "appointment" to ":appointment-service",
    "dispatch" to ":dispatch-service",
    "captain" to ":captain-service",
    "notification" to ":notification-service",
    "review" to ":review-service",
    "payment" to ":payment-service",
    "chat" to ":chat-service",
    "content" to ":content-service"
)

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    migrationProjects.forEach { (moduleId, projectPath) ->
        from(project(projectPath).layout.projectDirectory.dir("src/main/resources/db/migration")) {
            include("*.sql")
            into("db/migration/$moduleId")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("mypet.backendRoot", rootProject.projectDir.absolutePath)
}
