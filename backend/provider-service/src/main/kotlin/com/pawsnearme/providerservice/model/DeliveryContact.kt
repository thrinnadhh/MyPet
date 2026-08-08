package com.pawsnearme.providerservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "delivery_contacts", schema = "identity")
class DeliveryContact(
    @Id
    @Column(name = "address_id")
    var addressId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "phone_number", nullable = false, length = 13)
    var phoneNumber: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}
