package com.pawsnearme.orderservice.controller

import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Invoice
import com.pawsnearme.orderservice.service.AdminControlPlaneService
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import com.pawsnearme.orderservice.service.OrderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class AdminControllerAuthorizationTests {
    private val orderService: OrderService = mock()
    private val adminControlPlaneService: AdminControlPlaneService = mock()
    private val controller = AdminController(orderService, adminControlPlaneService)

    @Test
    fun `admin configuration rejects non-admin callers`() {
        assertThrows<OrderAccessDeniedException> {
            controller.getDisputeRefundMode("CUSTOMER")
        }
        verify(adminControlPlaneService, never()).getDisputeRefundMode()
    }

    @Test
    fun `list disputes rejects non-admin callers`() {
        assertThrows<OrderAccessDeniedException> {
            controller.listDisputes("CUSTOMER")
        }
        verify(adminControlPlaneService, never()).listDisputes(any(), any())
    }

    @Test
    fun `resolve dispute rejects non-admin callers`() {
        assertThrows<OrderAccessDeniedException> {
            controller.resolveDispute(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID().toString(),
                role = "CUSTOMER",
                requestId = "test-request",
                request = ResolveDisputeRequest("RESOLVED", "Customer tried to self-approve")
            )
        }
        verify(adminControlPlaneService, never()).resolveDispute(any(), any(), any(), any(), any())
    }

    @Test
    fun `resolve dispute rejects missing admin actor identity`() {
        assertThrows<OrderAccessDeniedException> {
            controller.resolveDispute(
                id = UUID.randomUUID(),
                userId = null,
                role = "ADMIN",
                requestId = "test-request",
                request = ResolveDisputeRequest("RESOLVED", "Approved after evidence review")
            )
        }
        verify(adminControlPlaneService, never()).resolveDispute(any(), any(), any(), any(), any())
    }

    @Test
    fun `create dispute passes authenticated identity to ownership-aware service`() {
        val orderId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val dispute = Dispute(
            disputeId = UUID.randomUUID(),
            orderId = orderId,
            status = "OPEN",
            reason = "Damaged item",
            createdAt = Instant.now()
        )
        whenever(orderService.createDisputeWithAuth(orderId, "Damaged item", actorId, "CUSTOMER"))
            .thenReturn(dispute)

        val response = controller.createDispute(
            actorId.toString(),
            "CUSTOMER",
            CreateDisputeRequest(orderId, "Damaged item")
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(dispute, response.body)
        verify(orderService).createDisputeWithAuth(orderId, "Damaged item", actorId, "CUSTOMER")
    }

    @Test
    fun `invoice lookup passes authenticated identity to ownership-aware service`() {
        val orderId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val invoice = Invoice(
            invoiceId = UUID.randomUUID(),
            orderId = orderId,
            invoiceNumber = "INV-TEST",
            subtotalAmount = BigDecimal("100.00"),
            taxAmount = BigDecimal("18.00"),
            totalAmount = BigDecimal("118.00"),
            generatedAt = Instant.now()
        )
        whenever(orderService.getInvoiceByOrderIdWithAuth(orderId, actorId, "CUSTOMER"))
            .thenReturn(invoice)

        val response = controller.getInvoice(orderId, actorId.toString(), "CUSTOMER")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(invoice, response.body)
        verify(orderService).getInvoiceByOrderIdWithAuth(orderId, actorId, "CUSTOMER")
    }
}
