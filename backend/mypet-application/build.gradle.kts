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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.2")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.2")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // M9 activates the existing bounded-context projects inside one Spring Boot
    // process. Their runtime dependencies are now deliberately transitive so
    // controllers, persistence, messaging and integrations are packaged into
    // the single mypet-application executable JAR.
    implementation(project(":common"))
    implementation(project(":provider-service"))
    implementation(project(":catalog-service"))
    implementation(project(":discovery-service"))
    implementation(project(":order-service"))
    implementation(project(":appointment-service"))
    implementation(project(":dispatch-service"))
    implementation(project(":captain-service"))
    implementation(project(":notification-service"))
    implementation(project(":review-service"))
    implementation(project(":payment-service"))
    implementation(project(":chat-service"))
    implementation(project(":content-service"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Preserve every service-owned migration and history table. The consolidated
// runtime orchestrates them sequentially against the existing schemas.
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
