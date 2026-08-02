package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.CustomerCase
import com.pawsnearme.orderservice.model.CustomerCaseEvidence
import com.pawsnearme.orderservice.repository.CustomerCaseEvidenceRepository
import com.pawsnearme.orderservice.repository.CustomerCaseRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CreateCustomerCaseRequest(
    val orderId: UUID,
    val caseType: String,
    val description: String
)

data class ResolveCustomerCaseRequest(
    val decision: String,
    val resolutionNotes: String,
    val issueRefund: Boolean = false,
    val refundStatus: String? = null
)

data class CustomerCaseEvidenceView(
    val evidenceId: UUID,
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Instant
)

data class CustomerCaseView(
    val caseId: UUID,
    val orderId: UUID,
    val customerId: UUID,
    val caseType: String,
    val description: String,
    val status: String,
    val refundStatus: String,
    val resolutionNotes: String?,
    val evidence: List<CustomerCaseEvidenceView>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?
)

data class CaseEvidenceReservation(val uploadToken: String, val uploadUrl: String, val expiresAt: Instant)
data class CaseEvidenceLink(val evidenceId: UUID, val url: String, val expiresAt: Instant)
data class CaseEvidenceContent(val bytes: ByteArray, val mimeType: String, val filename: String)

@Service
class CustomerCaseService(
    private val caseRepository: CustomerCaseRepository,
    private val evidenceRepository: CustomerCaseEvidenceRepository,
    private val orderRepository: OrderRepository,
    private val paymentModule: PaymentModuleApi,
    private val outboxService: OutboxService,
    @Value("\${order.case-evidence.dir:./private-case-evidence}") storageDir: String,
    @Value("\${order.case-evidence.public-base-url:http://localhost:8085}") private val publicBaseUrl: String,
    @Value("\${CASE_EVIDENCE_SIGNING_KEY:local-development-key}") private val signingKey: String
) {
    private data class PendingEvidence(val caseId: UUID, val actorId: UUID, val expiresAt: Instant)
    private val pending = ConcurrentHashMap<String, PendingEvidence>()
    private val root: Path = Paths.get(storageDir).toAbsolutePath().normalize()
    private val allowedTypes = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")

    init {
        require(signingKey.length >= 16) { "Case-evidence signing key must contain at least 16 characters." }
        Files.createDirectories(root)
    }

    @Transactional
    fun create(customerId: UUID, request: CreateCustomerCaseRequest): CustomerCaseView {
        val order = orderRepository.findById(request.orderId)
            .orElseThrow { IllegalArgumentException("Order not found.") }
        if (order.customerId != customerId) throw OrderAccessDeniedException("Order belongs to another customer.")
        val type = request.caseType.trim().uppercase()
        require(type in CASE_TYPES) { "Unsupported support case type." }
        val description = request.description.trim()
        require(description.length in 10..2000) { "Case description must contain between 10 and 2000 characters." }
        val saved = caseRepository.save(
            CustomerCase(
                orderId = request.orderId,
                customerId = customerId,
                caseType = type,
                description = description
            )
        )
        publish("CustomerCaseCreated", saved)
        return view(saved)
    }

    @Transactional(readOnly = true)
    fun listMine(customerId: UUID): List<CustomerCaseView> =
        caseRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).map(::view)

    @Transactional(readOnly = true)
    fun listAll(): List<CustomerCaseView> = caseRepository.findAllByOrderByCreatedAtDesc().map(::view)

    fun reserveEvidence(caseId: UUID, customerId: UUID): CaseEvidenceReservation {
        owned(caseId, customerId)
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(600)
        pending[token] = PendingEvidence(caseId, customerId, expiresAt)
        return CaseEvidenceReservation(
            token,
            "${publicBaseUrl.trimEnd('/')}/api/v1/orders/customer-cases/evidence/upload",
            expiresAt
        )
    }

    @Transactional
    fun storeEvidence(uploadToken: String, customerId: UUID, file: MultipartFile): CustomerCaseEvidenceView {
        val reservation = pending.remove(uploadToken)
            ?: throw IllegalArgumentException("Invalid or already-used evidence upload token.")
        if (reservation.actorId != customerId) throw OrderAccessDeniedException("Evidence token belongs to another customer.")
        if (reservation.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Evidence upload token has expired.")
        owned(reservation.caseId, customerId)
        validateFile(file)
        val mimeType = file.contentType!!.lowercase()
        val storageKey = "${UUID.randomUUID()}.${extension(mimeType)}"
        val destination = root.resolve(storageKey).normalize()
        if (!destination.startsWith(root)) throw IllegalArgumentException("Invalid evidence path.")
        Files.write(destination, file.bytes)
        val saved = evidenceRepository.save(
            CustomerCaseEvidence(
                caseId = reservation.caseId,
                uploaderUserId = customerId,
                originalFilename = sanitize(file.originalFilename),
                storageKey = storageKey,
                mimeType = mimeType,
                sizeBytes = file.size
            )
        )
        publish("CustomerCaseEvidenceAdded", owned(reservation.caseId, customerId), mapOf("evidenceId" to saved.evidenceId.toString()))
        return evidenceView(saved)
    }

    @Transactional
    fun resolve(caseId: UUID, request: ResolveCustomerCaseRequest, adminId: UUID): CustomerCaseView {
        val customerCase = caseRepository.findById(caseId)
            .orElseThrow { IllegalArgumentException("Customer case not found.") }
        require(customerCase.status in setOf("OPEN", "UNDER_REVIEW")) { "Customer case is already closed." }
        val decision = request.decision.trim().uppercase()
        require(decision in setOf("RESOLVED", "REJECTED", "UNDER_REVIEW")) { "Unsupported case decision." }
        val notes = request.resolutionNotes.trim()
        require(notes.length in 3..2000) { "Resolution notes are required." }
        customerCase.status = decision
        customerCase.resolutionNotes = notes
        customerCase.updatedAt = Instant.now()
        if (decision in setOf("RESOLVED", "REJECTED")) customerCase.resolvedAt = Instant.now()
        request.refundStatus?.trim()?.uppercase()?.let {
            require(it in REFUND_STATUSES) { "Unsupported refund status." }
            customerCase.refundStatus = it
        }
        if (request.issueRefund) {
            customerCase.refundStatus = "PROCESSING"
            paymentModule.refundOrder(customerCase.orderId)
        }
        val saved = caseRepository.save(customerCase)
        publish("CustomerCaseUpdated", saved, mapOf("adminId" to adminId.toString(), "decision" to decision))
        return view(saved)
    }

    @Transactional
    fun signedEvidenceLink(caseId: UUID, evidenceId: UUID, actorId: UUID, role: String?): CaseEvidenceLink {
        val customerCase = caseRepository.findById(caseId)
            .orElseThrow { IllegalArgumentException("Customer case not found.") }
        val allowed = role.equals("ADMIN", ignoreCase = true) || customerCase.customerId == actorId
        if (!allowed) throw OrderAccessDeniedException("Case evidence access denied.")
        val evidence = evidenceRepository.findById(evidenceId)
            .orElseThrow { IllegalArgumentException("Evidence not found.") }
        if (evidence.caseId != caseId) throw OrderAccessDeniedException("Evidence does not belong to this case.")
        val expiresAt = Instant.now().plusSeconds(300)
        val payload = "$evidenceId|$actorId|${expiresAt.epochSecond}"
        val token = encode(payload.toByteArray()) + "." + encode(hmac(payload.toByteArray()))
        val url = "${publicBaseUrl.trimEnd('/')}/api/v1/orders/customer-cases/evidence/$evidenceId/content?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
        return CaseEvidenceLink(evidenceId, url, expiresAt)
    }

    fun readSignedEvidence(evidenceId: UUID, token: String): CaseEvidenceContent {
        val parts = token.split('.', limit = 2)
        if (parts.size != 2) throw OrderAccessDeniedException("Invalid evidence token.")
        val payload = String(decode(parts[0]), StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(hmac(payload.toByteArray()), decode(parts[1]))) {
            throw OrderAccessDeniedException("Invalid evidence signature.")
        }
        val values = payload.split('|')
        if (values.size != 3 || UUID.fromString(values[0]) != evidenceId) throw OrderAccessDeniedException("Evidence token mismatch.")
        if (Instant.ofEpochSecond(values[2].toLong()).isBefore(Instant.now())) throw OrderAccessDeniedException("Evidence link expired.")
        val evidence = evidenceRepository.findById(evidenceId)
            .orElseThrow { IllegalArgumentException("Evidence not found.") }
        val path = root.resolve(evidence.storageKey).normalize()
        if (!path.startsWith(root) || !Files.exists(path)) throw IllegalStateException("Evidence content unavailable.")
        return CaseEvidenceContent(Files.readAllBytes(path), evidence.mimeType, evidence.originalFilename)
    }

    private fun owned(caseId: UUID, customerId: UUID): CustomerCase {
        val customerCase = caseRepository.findById(caseId)
            .orElseThrow { IllegalArgumentException("Customer case not found.") }
        if (customerCase.customerId != customerId) throw OrderAccessDeniedException("Customer case belongs to another customer.")
        return customerCase
    }

    private fun view(customerCase: CustomerCase) = CustomerCaseView(
        caseId = customerCase.caseId,
        orderId = customerCase.orderId,
        customerId = customerCase.customerId,
        caseType = customerCase.caseType,
        description = customerCase.description,
        status = customerCase.status,
        refundStatus = customerCase.refundStatus,
        resolutionNotes = customerCase.resolutionNotes,
        evidence = evidenceRepository.findByCaseIdOrderByCreatedAtAsc(customerCase.caseId).map(::evidenceView),
        createdAt = customerCase.createdAt,
        updatedAt = customerCase.updatedAt,
        resolvedAt = customerCase.resolvedAt
    )

    private fun evidenceView(evidence: CustomerCaseEvidence) = CustomerCaseEvidenceView(
        evidence.evidenceId,
        evidence.originalFilename,
        evidence.mimeType,
        evidence.sizeBytes,
        evidence.createdAt
    )

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) throw IllegalArgumentException("Evidence file is required.")
        if (file.size > MAX_BYTES) throw IllegalArgumentException("Evidence exceeds the 10 MB limit.")
        val mime = file.contentType?.lowercase() ?: throw IllegalArgumentException("Evidence type is required.")
        if (mime !in allowedTypes) throw IllegalArgumentException("Only PDF, JPEG, PNG and WebP evidence is supported.")
    }

    private fun sanitize(value: String?): String = value?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()?.take(255).orEmpty().ifBlank { "evidence" }
    private fun extension(mime: String) = when (mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
    private fun hmac(value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")); doFinal(value)
    }
    private fun encode(value: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)

    private fun publish(eventType: String, customerCase: CustomerCase, data: Map<String, Any?> = emptyMap()) {
        outboxService.saveEvent(
            UUID.randomUUID(),
            "CUSTOMER_CASE",
            customerCase.caseId,
            eventType,
            mapOf(
                "eventType" to eventType,
                "caseId" to customerCase.caseId.toString(),
                "orderId" to customerCase.orderId.toString(),
                "customerId" to customerCase.customerId.toString(),
                "status" to customerCase.status,
                "refundStatus" to customerCase.refundStatus,
                "data" to data,
                "occurredAt" to Instant.now().toString()
            )
        )
    }

    companion object {
        private const val MAX_BYTES = 10L * 1024 * 1024
        private val CASE_TYPES = setOf("MISSING_ITEM", "DAMAGED_ITEM", "WRONG_ITEM", "LATE_DELIVERY", "PAYMENT_ISSUE", "OTHER")
        private val REFUND_STATUSES = setOf("NOT_APPLICABLE", "PENDING", "PROCESSING", "COMPLETED", "FAILED")
    }
}
