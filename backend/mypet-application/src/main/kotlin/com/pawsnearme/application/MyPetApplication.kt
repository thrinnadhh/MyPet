package com.pawsnearme.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Single deployable shell for the MyPet modular-monolith migration.
 *
 * M1 intentionally scans only this package. Business modules remain isolated
 * until M2 imports them behind explicit module boundaries.
 */
@SpringBootApplication
class MyPetApplication

fun main(args: Array<String>) {
    runApplication<MyPetApplication>(*args)
}
