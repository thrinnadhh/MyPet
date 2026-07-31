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
