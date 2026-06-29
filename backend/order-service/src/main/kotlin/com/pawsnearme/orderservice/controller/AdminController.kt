package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Invoice
import com.pawsnearme.orderservice.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class AdminController(private val orderService: OrderService) {

    // --- System Config ---

    @GetMapping("/admin/config")
    fun getDisputeRefundMode(): ResponseEntity<Map<String, String>> {
        val mode = orderService.getDisputeRefundMode()
        return ResponseEntity.ok(mapOf("dispute_refund_mode" to mode))
    }

    @PostMapping("/admin/config")
    fun updateDisputeRefundMode(@RequestBody request: Map<String, String>): ResponseEntity<Map<String, String>> {
        val mode = request["dispute_refund_mode"] ?: throw IllegalArgumentException("Missing dispute_refund_mode")
        val updated = orderService.updateDisputeRefundMode(mode)
        return ResponseEntity.ok(mapOf("dispute_refund_mode" to updated))
    }

    // --- Disputes ---

    @GetMapping("/disputes")
    fun listDisputes(): ResponseEntity<List<Dispute>> {
        return ResponseEntity.ok(orderService.listDisputes())
    }

    @PostMapping("/disputes")
    fun createDispute(@RequestBody request: Map<String, String>): ResponseEntity<Dispute> {
        val orderIdStr = request["orderId"] ?: throw IllegalArgumentException("Missing orderId")
        val reason = request["reason"] ?: throw IllegalArgumentException("Missing reason")
        val orderId = UUID.fromString(orderIdStr)
        val dispute = orderService.createDispute(orderId, reason)
        return ResponseEntity.ok(dispute)
    }

    @PostMapping("/disputes/{id}/resolve")
    fun resolveDispute(
        @PathVariable id: UUID,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Dispute> {
        val decision = request["decision"] ?: throw IllegalArgumentException("Missing decision (RESOLVED/REJECTED)")
        val notes = request["resolutionNotes"]
        val dispute = orderService.resolveDispute(id, decision, notes)
        return ResponseEntity.ok(dispute)
    }

    // --- Invoices ---

    @GetMapping("/{orderId}/invoice")
    fun getInvoice(@PathVariable orderId: UUID): ResponseEntity<Invoice> {
        return ResponseEntity.ok(orderService.getInvoiceByOrderId(orderId))
    }
}
