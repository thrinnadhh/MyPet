package com.pawsnearme.providerservice.model

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "vaccination_reminders", schema = "identity")
class VaccinationReminder(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "reminder_id")
    var reminderId: UUID? = null,

    @Column(name = "pet_id", nullable = false)
    var petId: UUID,

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(name = "vaccine_name", nullable = false)
    var vaccineName: String,

    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate,

    @Column(name = "clinic_name")
    var clinicName: String? = null,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "pets", schema = "identity")
class Pet(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "pet_id")
    var petId: UUID? = null,

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "species", nullable = false)
    var species: String = "DOG",

    @Column(name = "breed")
    var breed: String? = null,

    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
