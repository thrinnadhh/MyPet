package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.service.CaseEvidenceLink
import com.pawsnearme.orderservice.service.CaseEvidenceReservation
import com.pawsnearme.orderservice.service.CreateCustomerCaseRequest
import com.pawsnearme.orderservice.service.CustomerCaseEvidenceView
import com.pawsnearme.orderservice.service.CustomerCaseService
import com.pawsnearme.orderservice.service.CustomerCaseView
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import com.pawsnearme.orderservice.service.ResolveCustomerCaseRequest
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/customer-cases")
class CustomerCaseController(
    private val customerCaseService: CustomerCaseService
) {
    @PostMapping
    fun create(
        @RequestBody request: CreateCustomerCaseRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<CustomerCaseView> = ResponseEntity.status(HttpStatus.CREATED)
        .body(customerCaseService.create(requireUser(userId), request))

    @GetMapping
    fun mine(
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<List<CustomerCaseView>> =
        ResponseEntity.ok(customerCaseService.listMine(requireUser(userId)))

    @GetMapping("/admin")
    fun all(
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<List<CustomerCaseView>> {
        requireAdmin(role)
        return ResponseEntity.ok(customerCaseService.listAll())
    }

    @PostMapping("/{caseId}/evidence/reservations")
    fun reserveEvidence(
        @PathVariable caseId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<CaseEvidenceReservation> =
        ResponseEntity.ok(customerCaseService.reserveEvidence(caseId, requireUser(userId)))

    @PostMapping("/evidence/upload")
    fun uploadEvidence(
        @RequestParam uploadToken: String,
        @RequestParam("file") file: MultipartFile,
        @RequestHeader("X-User-Id", required = false) userId: String?
    ): ResponseEntity<CustomerCaseEvidenceView> = ResponseEntity.status(HttpStatus.CREATED)
        .body(customerCaseService.storeEvidence(uploadToken, requireUser(userId), file))

    @PostMapping("/{caseId}/evidence/{evidenceId}/signed-link")
    fun signedEvidenceLink(
        @PathVariable caseId: UUID,
        @PathVariable evidenceId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<CaseEvidenceLink> = ResponseEntity.ok(
        customerCaseService.signedEvidenceLink(caseId, evidenceId, requireUser(userId), role)
    )

    @GetMapping("/evidence/{evidenceId}/content")
    fun evidenceContent(
        @PathVariable evidenceId: UUID,
        @RequestParam token: String
    ): ResponseEntity<ByteArray> {
        val content = customerCaseService.readSignedEvidence(evidenceId, token)
        val disposition = ContentDisposition.inline().filename(content.filename, StandardCharsets.UTF_8).build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.mimeType))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.bytes)
    }

    @PatchMapping("/{caseId}/admin")
    fun resolve(
        @PathVariable caseId: UUID,
        @RequestBody request: ResolveCustomerCaseRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) role: String?
    ): ResponseEntity<CustomerCaseView> {
        requireAdmin(role)
        return ResponseEntity.ok(customerCaseService.resolve(caseId, request, requireUser(userId)))
    }

    private fun requireAdmin(role: String?) {
        if (!role.equals("ADMIN", ignoreCase = true)) throw OrderAccessDeniedException("Administrator role required.")
    }

    private fun requireUser(value: String?): UUID = try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw OrderAccessDeniedException("Valid authenticated user context is required.")
    }
}
