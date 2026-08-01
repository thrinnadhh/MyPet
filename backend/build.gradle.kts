import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    kotlin("plugin.jpa") version "2.1.20" apply false
    id("org.springframework.boot") version "3.5.14" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.pawsnearme"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val monolithServiceProjects = setOf(
    "provider-service",
    "catalog-service",
    "discovery-service",
    "order-service",
    "appointment-service",
    "dispatch-service",
    "captain-service",
    "notification-service",
    "review-service",
    "payment-service",
    "chat-service",
    "content-service"
)

subprojects {
    if (name in monolithServiceProjects) {
        plugins.withId("org.springframework.boot") {
            val sourceSets = extensions.getByType<SourceSetContainer>()
            val monolithJar = tasks.register<Jar>("monolithJar") {
                group = "build"
                description = "Builds the reusable module artifact for mypet-application"
                archiveClassifier.set("monolith")
                from(sourceSets.named("main").map { it.output })

                // Standalone launch and persistence bootstrap remain owned by
                // each service bootJar until the later migration milestones.
                exclude("application.yml")
                exclude("application-*.yml")
                exclude("db/migration/**")
                exclude("**/*Application.class")
                exclude("**/*ApplicationKt.class")
            }

            configurations.create("monolithElements") {
                isCanBeConsumed = true
                isCanBeResolved = false
                extendsFrom(configurations.getByName("runtimeElements"))
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                    attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements.JAR)
                    )
                }
                outgoing.artifact(monolithJar)
            }
        }
    }
}
