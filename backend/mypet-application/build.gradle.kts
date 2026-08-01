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
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // M2 packages the reusable portions of every business service. These
    // custom variants exclude standalone launchers, service application.yml
    // files and Flyway resources while retaining the service bootJars for
    // rollback and parallel verification.
    implementation(project(mapOf("path" to ":provider-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":catalog-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":discovery-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":order-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":appointment-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":dispatch-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":captain-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":notification-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":review-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":payment-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":chat-service", "configuration" to "monolithElements")))
    implementation(project(mapOf("path" to ":content-service", "configuration" to "monolithElements")))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
