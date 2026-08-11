package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Category
import com.pawsnearme.catalogservice.repository.CategoryRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateCategoryRequest(
    @field:NotBlank val slug: String,
    @field:NotBlank val name: String,
    val petType: String = "ALL",
    val parentId: UUID? = null,
    val imageUrl: String? = null,
    val sortOrder: Int = 0
)

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(private val categoryRepository: CategoryRepository) {

    @GetMapping
    fun getAllCategories(@RequestParam(required = false) petType: String?): ResponseEntity<List<Category>> {
        val categories = if (!petType.isNullOrBlank()) {
            categoryRepository.findByPetType(petType.trim().uppercase())
        } else {
            categoryRepository.findAll()
        }
        return ResponseEntity.ok(categories.sortedBy { it.sortOrder })
    }

    @GetMapping("/{id}")
    fun getCategoryById(@PathVariable id: UUID): ResponseEntity<Category> {
        val cat = categoryRepository.findById(id).orElseThrow { NoSuchElementException("Category not found") }
        return ResponseEntity.ok(cat)
    }

    @PostMapping
    fun createCategory(
        @Valid @RequestBody request: CreateCategoryRequest,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<Category> {
        if (role != "ADMIN") {
            throw CatalogAccessDeniedException("Only admins can manage categories")
        }
        val category = Category(
            slug = request.slug.trim().lowercase(),
            name = request.name.trim(),
            petType = request.petType.trim().uppercase(),
            parentId = request.parentId,
            imageUrl = request.imageUrl,
            sortOrder = request.sortOrder
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryRepository.save(category))
    }
}
