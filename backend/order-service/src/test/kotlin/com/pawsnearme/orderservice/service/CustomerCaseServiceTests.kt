package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.CustomerCase
import com.pawsnearme.orderservice.model.CustomerCaseEvidence
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.CustomerCaseEvidenceRepository
import com.pawsnearme.orderservice.repository.CustomerCaseRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.nio.file.Files
import java.util.Optional
import java.util.UUID

class CustomerCaseServiceTests {
    private val caseRepository: CustomerCaseRepository = mock()
    private val evidenceRepository: CustomerCaseEvidenceRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val outboxService: OutboxService = mock()
    private val root = Files.createTempDirectory("mypet-case-test")
    private val service = CustomerCaseService(
        caseRepository,
        evidenceRepository,
        orderRepository,
        paymentModule,
        outboxService,
        root.toString(),
        "http://localhost:8085",
        "0123456789abcdef0123456789abcdef"
    )

    private val customerId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @Test
    fun `customer creates case only for owned order`() {
        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order(customerId)))
        whenever(caseRepository.save(any<CustomerCase>())).thenAnswer { it.getArgument(0) }
        whenever(evidenceRepository.findByCaseIdOrderByCreatedAtAsc(any())).thenReturn(emptyList())

        val created = service.create(customerId, CreateCustomerCaseRequest(orderId, "DAMAGED_ITEM", "The sealed packet arrived torn."))

        assertEquals("DAMAGED_ITEM", created.caseType)
        assertEquals("OPEN", created.status)
        verify(outboxService).saveEvent(any(), eq("CUSTOMER_CASE"), any(), eq("CustomerCaseCreated"), any())

        assertThrows<OrderAccessDeniedException> {
            service.create(UUID.randomUUID(), CreateCustomerCaseRequest(orderId, "DAMAGED_ITEM", "The packet arrived damaged."))
        }
    }

    @Test
    fun `evidence upload reservation is single use and private`() {
        val customerCase = customerCase()
        whenever(caseRepository.findById(customerCase.caseId)).thenReturn(Optional.of(customerCase))
        whenever(evidenceRepository.save(any<CustomerCaseEvidence>())).thenAnswer { it.getArgument(0) }
        val reservation = service.reserveEvidence(customerCase.caseId, customerId)
        val file = MockMultipartFile(
            "file",
            "damage.jpg",
            "image/jpeg",
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00),
        )

        val evidence = service.storeEvidence(reservation.uploadToken, customerId, file)

        assertEquals("damage.jpg", evidence.originalFilename)
        assertTrue(Files.list(root).use { it.findAny().isPresent })
        assertThrows<IllegalArgumentException> {
            service.storeEvidence(reservation.uploadToken, customerId, file)
        }
    }

    @Test
    fun `evidence upload rejects spoofed declared content type`() {
        val customerCase = customerCase()
        whenever(caseRepository.findById(customerCase.caseId)).thenReturn(Optional.of(customerCase))
        val reservation = service.reserveEvidence(customerCase.caseId, customerId)
        val fakePdf = MockMultipartFile("file", "fake.pdf", "application/pdf", "this is not a pdf".toByteArray())

        assertThrows<IllegalArgumentException> {
            service.storeEvidence(reservation.uploadToken, customerId, fakePdf)
        }
    }

    @Test
    fun `admin resolution can initiate refund and records processing state`() {
        val customerCase = customerCase()
        whenever(caseRepository.findById(customerCase.caseId)).thenReturn(Optional.of(customerCase))
        whenever(caseRepository.save(any<CustomerCase>())).thenAnswer { it.getArgument(0) }
        whenever(evidenceRepository.findByCaseIdOrderByCreatedAtAsc(customerCase.caseId)).thenReturn(emptyList())

        val resolved = service.resolve(
            customerCase.caseId,
            ResolveCustomerCaseRequest("RESOLVED", "Refund approved after evidence review.", issueRefund = true),
            UUID.randomUUID()
        )

        assertEquals("RESOLVED", resolved.status)
        assertEquals("PROCESSING", resolved.refundStatus)
        verify(paymentModule).refundOrder(orderId)
    }

    @Test
    fun `rejected case never starts refund`() {
        val customerCase = customerCase()
        whenever(caseRepository.findById(customerCase.caseId)).thenReturn(Optional.of(customerCase))
        whenever(caseRepository.save(any<CustomerCase>())).thenAnswer { it.getArgument(0) }
        whenever(evidenceRepository.findByCaseIdOrderByCreatedAtAsc(customerCase.caseId)).thenReturn(emptyList())

        service.resolve(
            customerCase.caseId,
            ResolveCustomerCaseRequest("REJECTED", "Evidence does not match the delivered order."),
            UUID.randomUUID()
        )

        verify(paymentModule, never()).refundOrder(any())
    }

    private fun customerCase() = CustomerCase(
        orderId = orderId,
        customerId = customerId,
        caseType = "DAMAGED_ITEM",
        description = "The packet arrived damaged."
    )

    private fun order(owner: UUID) = Order(
        orderId = orderId,
        customerId = owner,
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = OrderStatus.DELIVERED,
        subtotalAmount = BigDecimal("450"),
        totalAmount = BigDecimal("450")
    )
}
