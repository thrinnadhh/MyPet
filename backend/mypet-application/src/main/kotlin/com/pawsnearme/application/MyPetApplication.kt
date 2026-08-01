package com.pawsnearme.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Single deployable shell for the MyPet modular-monolith migration.
 *
 * M2 links business modules as explicit, non-transitive libraries. Component
 * scanning remains restricted to this application package, so legacy service
 * boot entry points and infrastructure remain dormant until later milestones
 * activate them behind reviewed module APIs.
 */
@SpringBootApplication
class MyPetApplication

fun main(args: Array<String>) {
    runApplication<MyPetApplication>(*args)
}
