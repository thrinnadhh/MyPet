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

@Entity
@Table(name = "medical_reports", schema = "identity")
class MedicalReport(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "report_id")
    var reportId: UUID? = null,

    @Column(name = "pet_id", nullable = false)
    var petId: UUID,

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "category", nullable = false)
    var category: String, // 'BLOOD_TEST', 'VACCINATION', 'PRESCRIPTION', 'GENERAL'

    @Column(name = "lab_or_clinic_name")
    var labOrClinicName: String? = null,

    @Column(name = "doctor_name")
    var doctorName: String? = null,

    @Column(name = "object_key", nullable = false)
    var objectKey: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

data class CreateMedicalReportRequest(
    val title: String,
    val category: String,
    val labOrClinicName: String?,
    val doctorName: String?,
    val objectKey: String
)

data class MedicalReportDto(
    val reportId: UUID,
    val petId: UUID,
    val ownerId: UUID,
    val title: String,
    val category: String,
    val labOrClinicName: String?,
    val doctorName: String?,
    val signedUrl: String,
    val createdAt: Instant
)

