package com.pawsnearme.captainservice.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class CaptainStatus {
    PENDING_APPROVAL, ACTIVE, SUSPENDED, REJECTED
}

enum class VehicleType {
    BIKE, SCOOTER, BICYCLE, ON_FOOT
}

@Entity
@Table(name = "captain_profiles", schema = "captains")
class CaptainProfile(
    @Id
    @Column(name = "captain_id")
    var captainId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CaptainStatus = CaptainStatus.PENDING_APPROVAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    var vehicleType: VehicleType,

    @Column(name = "vehicle_number")
    var vehicleNumber: String? = null,

    @Column(name = "license_doc_url")
    var licenseDocUrl: String? = null,

    @Column(name = "bank_account")
    var bankAccount: String? = null,

    @Column(name = "bank_ifsc")
    var bankIfsc: String? = null,

    @Column(name = "selfie_doc_url")
    var selfieDocUrl: String? = null,

    @Column(name = "rating_avg", precision = 3, scale = 2)
    var ratingAvg: BigDecimal = BigDecimal("0.00"),

    @Column(name = "rating_count")
    var ratingCount: Int = 0,

    @Column(name = "total_deliveries", nullable = false)
    var totalDeliveries: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "captain_earnings", schema = "captains")
class CaptainEarning(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "earning_id")
    var earningId: UUID? = null,

    @Column(name = "captain_id", nullable = false)
    var captainId: UUID,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "amount", nullable = false)
    var amount: BigDecimal,

    @Column(name = "earned_at", nullable = false)
    var earnedAt: Instant = Instant.now(),

    @Column(name = "payout_id")
    var payoutId: UUID? = null
)

@Entity
@Table(name = "captain_documents", schema = "captains")
class CaptainDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "document_id")
    var documentId: UUID? = null,

    @Column(name = "captain_id", nullable = false)
    var captainId: UUID,

    @Column(name = "doc_type", nullable = false)
    var docType: String,

    @Column(name = "doc_url", nullable = false)
    var docUrl: String,

    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: Instant = Instant.now()
)
