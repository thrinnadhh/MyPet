package com.pawsnearme.orderservice

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.orderservice.service.OrderService
import com.pawsnearme.orderservice.service.QuoteStore
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
@ActiveProfiles("test")
class OrderServiceApplicationTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired(required = false)
    private var orderService: OrderService? = null

    @Autowired(required = false)
    private var quoteStore: QuoteStore? = null

    @Test
    fun `context loads and beans wire correctly`() {
        assertNotNull(objectMapper, "ObjectMapper bean should be initialized")
        assertNotNull(orderService, "OrderService bean should be initialized")
        assertNotNull(quoteStore, "QuoteStore bean should be initialized")
    }
}
