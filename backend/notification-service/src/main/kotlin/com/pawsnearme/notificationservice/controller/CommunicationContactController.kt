package com.pawsnearme.notificationservice.controller

import com.pawsnearme.notificationservice.model.NotificationContact
import com.pawsnearme.notificationservice.repository.NotificationContactRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class CommunicationContactController(
    private val contactRepository: NotificationContactRepository,
) {
    @PostMapping("/contact/me")
    fun syncMyContact(
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-User-Email", required = false) email: String?,
        @RequestHeader("X-User-Full-Name", required = false) fullName: String?,
        @RequestHeader("X-User-Phone", required = false) phone: String?,
    ): ResponseEntity<Map<String, Any?>> {
        val id = UUID.fromString(userId)
        val normalizedEmail = email?.trim()?.lowercase()?.takeIf { it.length <= 320 && EMAIL_REGEX.matches(it) }
        val normalizedName = fullName?.trim()?.take(200)?.takeIf { it.isNotBlank() }
        val normalizedPhone = phone?.trim()?.take(32)?.takeIf { it.isNotBlank() }
        val existing = contactRepository.findById(id).orElse(null)
        val contact = existing ?: NotificationContact(userId = id)

        if (normalizedEmail != null) contact.email = normalizedEmail
        if (normalizedName != null) contact.displayName = normalizedName
        if (normalizedPhone != null) contact.phone = normalizedPhone
        contact.updatedAt = Instant.now()
        contactRepository.save(contact)

        return ResponseEntity.ok(
            mapOf(
                "userId" to id,
                "emailAvailable" to !contact.email.isNullOrBlank(),
                "phoneAvailable" to !contact.phone.isNullOrBlank(),
                "updatedAt" to contact.updatedAt,
            )
        )
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
