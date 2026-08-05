package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Pet
import com.pawsnearme.providerservice.repository.PetRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

data class PetRequest(
    @field:NotBlank
    @field:Size(max = 80)
    val name: String,
    @field:NotBlank
    @field:Size(max = 30)
    val species: String,
    @field:Size(max = 80)
    val breed: String? = null,
    val dateOfBirth: LocalDate? = null,
)

data class PetResponse(
    val petId: UUID,
    val name: String,
    val species: String,
    val breed: String?,
    val dateOfBirth: LocalDate?,
)

@RestController
@RequestMapping("/api/v1/pets")
class PetController(
    private val petRepository: PetRepository,
) {
    @GetMapping
    fun listPets(
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?,
    ): ResponseEntity<List<PetResponse>> {
        val ownerId = parseUserId(userIdHeader)
        return ResponseEntity.ok(
            petRepository.findByOwnerId(ownerId)
                .sortedBy { it.name.lowercase() }
                .map(::toResponse),
        )
    }

    @PostMapping
    fun createPet(
        @Valid @RequestBody request: PetRequest,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?,
    ): ResponseEntity<PetResponse> {
        val ownerId = parseUserId(userIdHeader)
        val saved = petRepository.save(
            Pet(
                ownerId = ownerId,
                name = request.name.trim(),
                species = request.species.trim().uppercase(),
                breed = request.breed?.trim()?.takeIf(String::isNotBlank),
                dateOfBirth = request.dateOfBirth,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved))
    }

    @PutMapping("/{petId}")
    fun updatePet(
        @PathVariable petId: UUID,
        @Valid @RequestBody request: PetRequest,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?,
    ): ResponseEntity<PetResponse> {
        val ownerId = parseUserId(userIdHeader)
        val pet = requireOwnedPet(petId, ownerId)
        pet.name = request.name.trim()
        pet.species = request.species.trim().uppercase()
        pet.breed = request.breed?.trim()?.takeIf(String::isNotBlank)
        pet.dateOfBirth = request.dateOfBirth
        return ResponseEntity.ok(toResponse(petRepository.save(pet)))
    }

    @DeleteMapping("/{petId}")
    fun deletePet(
        @PathVariable petId: UUID,
        @RequestHeader("X-User-Id", required = false) userIdHeader: String?,
    ): ResponseEntity<Unit> {
        val ownerId = parseUserId(userIdHeader)
        petRepository.delete(requireOwnedPet(petId, ownerId))
        return ResponseEntity.noContent().build()
    }

    private fun parseUserId(userIdHeader: String?): UUID {
        if (userIdHeader.isNullOrBlank()) {
            throw ProviderAccessDeniedException("Unauthorized: user context missing")
        }
        return runCatching { UUID.fromString(userIdHeader) }
            .getOrElse { throw ProviderAccessDeniedException("Unauthorized: invalid user context") }
    }

    private fun requireOwnedPet(petId: UUID, ownerId: UUID): Pet {
        val pet = petRepository.findById(petId)
            .orElseThrow { NoSuchElementException("Pet not found") }
        if (pet.ownerId != ownerId) {
            throw ProviderAccessDeniedException("Access denied: pet belongs to another customer")
        }
        return pet
    }

    private fun toResponse(pet: Pet): PetResponse = PetResponse(
        petId = requireNotNull(pet.petId),
        name = pet.name,
        species = pet.species,
        breed = pet.breed,
        dateOfBirth = pet.dateOfBirth,
    )
}
