package com.pawsnearme.appointmentservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class AppointmentStatus {
    SLOT_HELD, PAID, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, EXPIRED
}

@Entity
@Table(name = "appointments", schema = "appointments")
class Appointment(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "appointment_id")
    var appointmentId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "provider_id", nullable = false)
    var providerId: UUID,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "slot_id", nullable = false)
    var slotId: UUID,

    @Column(name = "pet_id", nullable = false)
    var petId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AppointmentStatus = AppointmentStatus.SLOT_HELD,

    @Column(name = "price_amount", nullable = false)
    var priceAmount: BigDecimal,

    @Column(name = "payment_id")
    var paymentId: UUID? = null,

    @Column(name = "pay_at_clinic", nullable = false)
    var payAtClinic: Boolean = false,

    @Column(name = "visit_notes")
    var visitNotes: String? = null,

    @Column(name = "prescription_doc_url")
    var prescriptionDocUrl: String? = null,

    @Column(name = "booked_at", nullable = false)
    var bookedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "cancellation_reason")
    var cancellationReason: String? = null
)

@Entity
@Table(name = "appointment_status_history", schema = "appointments")
class AppointmentStatusHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "history_id")
    var historyId: UUID? = null,

    @Column(name = "appointment_id", nullable = false)
    var appointmentId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    var fromStatus: AppointmentStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    var toStatus: AppointmentStatus,

    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now(),

    @Column(name = "changed_by_user_id")
    var changedByUserId: UUID? = null,

    @Column(name = "note")
    var note: String? = null
)

@Entity
@Table(name = "appointment_invoices", schema = "appointments")
class AppointmentInvoice(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "invoice_id")
    var invoiceId: UUID? = null,

    @Column(name = "appointment_id", nullable = false, unique = true)
    var appointmentId: UUID,

    @Column(name = "invoice_number", nullable = false, unique = true)
    var invoiceNumber: String,

    @Column(name = "subtotal_amount", nullable = false)
    var subtotalAmount: BigDecimal,

    @Column(name = "tax_amount", nullable = false)
    var taxAmount: BigDecimal,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: BigDecimal,

    @Column(name = "generated_at", nullable = false)
    var generatedAt: Instant = Instant.now()
)
