package com.pawsnearme.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Single deployable shell for the MyPet modular-monolith migration.
 *
 * M2 links business modules as explicit, non-transitive libraries. M3 adds the
 * servlet-native security and API-edge boundary behind an explicit enablement
 * switch. Component scanning remains restricted to this application package,
 * so legacy service boot entry points and infrastructure stay dormant until
 * their owning migration milestones activate them.
 */
@SpringBootApplication
class MyPetApplication

fun main(args: Array<String>) {
    runApplication<MyPetApplication>(*args)
}
