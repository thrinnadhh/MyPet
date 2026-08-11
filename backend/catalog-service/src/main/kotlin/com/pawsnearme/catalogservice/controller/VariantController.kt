package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.OfferingVariant
import com.pawsnearme.catalogservice.repository.OfferingVariantRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class CreateVariantRequest(
    @field:NotNull val offeringId: UUID,
    @field:NotBlank val name: String,
    @field:NotNull @field:DecimalMin("0.0") val price: BigDecimal,
    @field:Min(0) val stockQuantity: Int = 0,
    val sku: String? = null,
    val sortOrder: Int = 0
)

@RestController
@RequestMapping("/api/v1/variants")
class VariantController(private val variantRepository: OfferingVariantRepository) {

    @GetMapping("/offering/{offeringId}")
    fun getVariantsByOffering(@PathVariable offeringId: UUID): ResponseEntity<List<OfferingVariant>> {
        return ResponseEntity.ok(variantRepository.findByOfferingIdOrderBySortOrderAsc(offeringId))
    }

    @PostMapping
    fun createVariant(
        @Valid @RequestBody request: CreateVariantRequest,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<OfferingVariant> {
        val variant = OfferingVariant(
            offeringId = request.offeringId,
            name = request.name,
            price = request.price,
            stockQuantity = request.stockQuantity,
            sku = request.sku,
            sortOrder = request.sortOrder
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(variantRepository.save(variant))
    }
}
