pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("org.jetbrains.kotlin")) {
                useVersion("2.1.20")
            }
        }
    }
}

rootProject.name = "pawsnearme-backend"

include("api-gateway")
include("provider-service")
include("catalog-service")
include("discovery-service")
include("order-service")
include("appointment-service")
