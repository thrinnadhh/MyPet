package com.pawsnearme.discoveryservice.controller

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Entity
@Table(name = "city_launch_requests", schema = "providers")
class CityLaunchRequest(
    @Id
    @Column(name = "request_id")
    var requestId: UUID = UUID.randomUUID(),

    @Column(name = "city_name", nullable = false, length = 120)
    var cityName: String,

    @Column(name = "normalized_city", nullable = false, length = 120)
    var normalizedCity: String,

    @Column(name = "contact_info", nullable = false, length = 254)
    var contactInfo: String,

    @Column(name = "normalized_contact", nullable = false, length = 254)
    var normalizedContact: String,

    @Column(name = "source", nullable = false, length = 40)
    var source: String = "CUSTOMER_APP",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Repository
interface CityLaunchRequestRepository : JpaRepository<CityLaunchRequest, UUID> {
    fun findFirstByNormalizedCityAndNormalizedContact(
        normalizedCity: String,
        normalizedContact: String,
    ): CityLaunchRequest?
}

data class CityLaunchRequestInput(
    val cityName: String,
    val contactInfo: String,
)

data class CityLaunchRequestResponse(
    val requestId: UUID,
    val cityName: String,
    val status: String,
    val createdAt: Instant,
)

@RestController
@RequestMapping("/api/v1/service-regions/launch-requests")
class CityLaunchRequestController(
    private val repository: CityLaunchRequestRepository,
) {
    @PostMapping
    fun create(
        @RequestBody input: CityLaunchRequestInput,
    ): ResponseEntity<CityLaunchRequestResponse> {
        val cityName = input.cityName.trim().replace(Regex("\\s+"), " ")
        val contactInfo = input.contactInfo.trim()
        require(cityName.isNotBlank() && cityName.length <= 120) {
            "Enter a city name between 1 and 120 characters."
        }
        require(contactInfo.isNotBlank() && contactInfo.length <= 254) {
            "Enter contact information between 1 and 254 characters."
        }
        require(validContact(contactInfo)) {
            "Enter a valid email address or Indian mobile number."
        }

        val normalizedCity = cityName.lowercase(Locale.ROOT)
        val normalizedContact = normalizeContact(contactInfo)
        val existing = repository.findFirstByNormalizedCityAndNormalizedContact(
            normalizedCity,
            normalizedContact,
        )
        if (existing != null) {
            existing.cityName = cityName
            existing.contactInfo = contactInfo
            existing.updatedAt = Instant.now()
            return ResponseEntity.ok(existing.toResponse("ALREADY_REGISTERED"))
        }

        val saved = try {
            repository.save(
                CityLaunchRequest(
                    cityName = cityName,
                    normalizedCity = normalizedCity,
                    contactInfo = contactInfo,
                    normalizedContact = normalizedContact,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            repository.findFirstByNormalizedCityAndNormalizedContact(
                normalizedCity,
                normalizedContact,
            ) ?: throw IllegalStateException("Could not persist launch request")
        }

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(saved.toResponse("REGISTERED"))
    }

    private fun validContact(value: String): Boolean {
        val email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val digits = value.filter(Char::isDigit)
        val phone = digits.length == 10 || (digits.length == 12 && digits.startsWith("91"))
        return email.matches(value.lowercase(Locale.ROOT)) || phone
    }

    private fun normalizeContact(value: String): String {
        if (value.contains('@')) return value.lowercase(Locale.ROOT)
        val digits = value.filter(Char::isDigit)
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> digits
        }
    }

    private fun CityLaunchRequest.toResponse(status: String) = CityLaunchRequestResponse(
        requestId = requestId,
        cityName = cityName,
        status = status,
        createdAt = createdAt,
    )
}
