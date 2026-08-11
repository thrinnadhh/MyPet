package com.pawsnearme.orderservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.common.idempotency.ProcessedEventRepository
import com.pawsnearme.common.outbox.OutboxPoller
import com.pawsnearme.common.outbox.OutboxRepository
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.service.CheckoutIntegrityService
import com.pawsnearme.orderservice.service.CheckoutQuoteResponse
import com.pawsnearme.orderservice.service.CustomerDeliveryContact
import com.pawsnearme.orderservice.service.CustomerOrderCancellationView
import com.pawsnearme.orderservice.service.CustomerOrderDeliveryAddressView
import com.pawsnearme.orderservice.service.CustomerOrderDeliveryContactView
import com.pawsnearme.orderservice.service.CustomerOrderDetailResponse
import com.pawsnearme.orderservice.service.CustomerOrderPaymentView
import com.pawsnearme.orderservice.service.CustomerOrderPricingView
import com.pawsnearme.orderservice.service.CustomerOrderProjectionService
import com.pawsnearme.orderservice.service.CustomerOrderTimestampsView
import com.pawsnearme.orderservice.service.CustomerProviderView
import com.pawsnearme.orderservice.service.DeliveryContactLookup
import com.pawsnearme.orderservice.service.OrderService
import com.pawsnearme.orderservice.service.QuoteStore
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var orderService: OrderService

    @MockBean
    private lateinit var checkoutIntegrityService: CheckoutIntegrityService

    @MockBean
    private lateinit var orderRepository: OrderRepository

    @MockBean
    private lateinit var deliveryContactLookup: DeliveryContactLookup

    @MockBean
    private lateinit var customerOrderProjectionService: CustomerOrderProjectionService

    @MockBean
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @MockBean
    private lateinit var quoteStore: QuoteStore

    @MockBean
    private lateinit var outboxRepository: OutboxRepository

    @MockBean
    private lateinit var outboxService: OutboxService

    @MockBean
    private lateinit var outboxPoller: OutboxPoller

    @MockBean
    private lateinit var processedEventRepository: ProcessedEventRepository

    @MockBean
    private lateinit var idempotencyService: IdempotencyService

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Test
    fun `POST checkout quote - success with JSON mapping and X-User-Id header`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val deliveryAddressId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()

        val quoteResponse = CheckoutQuoteResponse(
            quoteToken = "Q-TEST12345",
            subtotal = BigDecimal("500.00"),
            itemDiscount = BigDecimal.ZERO,
            couponDiscount = BigDecimal.ZERO,
            loyaltyDiscount = BigDecimal.ZERO,
            deliveryFee = BigDecimal.ZERO,
            tax = BigDecimal("25.00"),
            roundOff = BigDecimal.ZERO,
            payableTotal = BigDecimal("525.00"),
            expiresAt = Instant.now().plusSeconds(900)
        )

        whenever(checkoutIntegrityService.calculateQuote(any())).thenReturn(quoteResponse)

        val jsonRequest = """
            {
              "providerId": "$providerId",
              "deliveryAddressId": "$deliveryAddressId",
              "items": [
                {
                  "offeringId": "$offeringId",
                  "quantity": 2
                }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/checkout/quote")
                .header("X-User-Id", customerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quoteToken").value("Q-TEST12345"))
            .andExpect(jsonPath("$.payableTotal").value(525.00))
    }

    @Test
    fun `POST checkout quote - missing X-User-Id returns 401`() {
        val jsonRequest = """
            {
              "providerId": "${UUID.randomUUID()}",
              "deliveryAddressId": "${UUID.randomUUID()}",
              "items": [{"offeringId": "${UUID.randomUUID()}", "quantity": 1}]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/checkout/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST create order - success snapshots owned delivery contact without trusting client header`() {
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val deliveryAddressId = UUID.randomUUID()
        val offeringId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val idempotencyKey = "checkout:Q-TEST12345"

        val createdOrder = Order(
            orderId = orderId,
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = deliveryAddressId,
            status = OrderStatus.PLACED,
            subtotalAmount = BigDecimal("500.00"),
            totalAmount = BigDecimal("525.00"),
            paymentMethod = "COD"
        )

        whenever(deliveryContactLookup.forCustomerAddress(customerId, deliveryAddressId))
            .thenReturn(CustomerDeliveryContact("+919876543210"))
        whenever(checkoutIntegrityService.createOrder(any(), eq(idempotencyKey))).thenReturn(createdOrder)
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }
        whenever(customerOrderProjectionService.detail(orderId, customerId, "CUSTOMER"))
            .thenReturn(canonicalDetail(orderId, providerId, deliveryAddressId, OrderStatus.PLACED, "COD", PaymentStatus.COD_PENDING))

        val jsonRequest = """
            {
              "quoteToken": "Q-TEST12345",
              "providerId": "$providerId",
              "deliveryAddressId": "$deliveryAddressId",
              "items": [
                {
                  "offeringId": "$offeringId",
                  "quantity": 2
                }
              ],
              "paymentMethod": "COD"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/orders")
                .header("X-User-Id", customerId.toString())
                .header("X-User-Phone", "+919876543210")
                .header("X-Idempotency-Key", idempotencyKey)
                .header("X-Delivery-Contact-Phone", "+919999999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.status").value("PLACED"))
            .andExpect(jsonPath("$.deliveryContact.phone").value("+919876543210"))
            .andExpect(jsonPath("$.deliveryContact.verified").value(true))
    }

    @Test
    fun `POST create order - rejects address without customer-owned delivery contact`() {
        val customerId = UUID.randomUUID()
        val deliveryAddressId = UUID.randomUUID()
        val jsonRequest = """
            {
              "quoteToken": "Q-TEST12345",
              "providerId": "${UUID.randomUUID()}",
              "deliveryAddressId": "$deliveryAddressId",
              "items": [{"offeringId": "${UUID.randomUUID()}", "quantity": 1}],
              "paymentMethod": "COD"
            }
        """.trimIndent()

        whenever(deliveryContactLookup.forCustomerAddress(customerId, deliveryAddressId)).thenReturn(null)

        mockMvc.perform(
            post("/api/v1/orders")
                .header("X-User-Id", customerId.toString())
                .header("X-Delivery-Contact-Phone", "+919999999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("DELIVERY_CONTACT_REQUIRED"))
    }

    @Test
    fun `GET order by ID - authorized caller returns canonical customer response`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val deliveryAddressId = UUID.randomUUID()
        whenever(customerOrderProjectionService.detail(orderId, customerId, "CUSTOMER"))
            .thenReturn(canonicalDetail(orderId, providerId, deliveryAddressId, OrderStatus.ACCEPTED, "ONLINE", PaymentStatus.SUCCESS))

        mockMvc.perform(
            get("/api/v1/orders/$orderId")
                .header("X-User-Id", customerId.toString())
                .header("X-User-Role", "CUSTOMER")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.provider.providerId").value(providerId.toString()))
            .andExpect(jsonPath("$.payment.status").value("SUCCESS"))
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
    }

    private fun canonicalDetail(
        orderId: UUID,
        providerId: UUID,
        deliveryAddressId: UUID,
        status: OrderStatus,
        paymentMethod: String,
        paymentStatus: PaymentStatus,
    ) = CustomerOrderDetailResponse(
        orderId = orderId,
        provider = CustomerProviderView(providerId, "Test Provider", "PET_STORE"),
        items = emptyList(),
        pricing = CustomerOrderPricingView(
            subtotal = BigDecimal("500.00"),
            discount = BigDecimal.ZERO,
            loyaltyDiscount = BigDecimal.ZERO,
            delivery = BigDecimal.ZERO,
            tax = BigDecimal("25.00"),
            total = BigDecimal("525.00"),
        ),
        payment = CustomerOrderPaymentView(paymentMethod, paymentStatus, null),
        status = status,
        flowStep = status.name.lowercase(),
        statusHistory = emptyList(),
        deliveryAddress = CustomerOrderDeliveryAddressView(
            addressId = deliveryAddressId,
            label = "Home",
            line1 = "Test Street",
            line2 = null,
            city = "Tirupati",
            state = "Andhra Pradesh",
            pincode = "517501",
            latitude = 13.6288,
            longitude = 79.4192,
        ),
        deliveryContact = CustomerOrderDeliveryContactView("+919876543210", true),
        captain = null,
        timestamps = CustomerOrderTimestampsView(
            placedAt = Instant.parse("2026-08-11T08:00:00Z"),
            acceptedAt = null,
            preparingAt = null,
            readyAt = null,
            pickedUpAt = null,
            deliveredAt = null,
            cancelledAt = null,
        ),
        cancellation = CustomerOrderCancellationView(false, null, null),
        invoice = null,
    )
}
