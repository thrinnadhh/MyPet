package com.pawsnearme.paymentservice.repository

import jakarta.persistence.LockModeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

class TransactionRepositoryConcurrencyContractTests {
    @Test
    fun `financial reference lookup uses pessimistic row lock`() {
        val method = TransactionRepository::class.java.methods.single {
            it.name == "findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc"
        }
        val lock = method.getAnnotation(Lock::class.java)

        assertNotNull(lock, "Financial check-then-act lookup must retain an explicit row lock")
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value)
    }

    @Test
    fun `gateway payment webhook lookup locks row and excludes refund lifecycle states`() {
        val method = TransactionRepository::class.java.methods.single {
            it.name == "findByGatewayTransactionId"
        }
        val query = method.getAnnotation(Query::class.java)

        assertNotNull(query, "Webhook transaction lookup must remain an explicit financial-state query")
        assertTrue(query.nativeQuery, "Webhook transaction lookup must use the schema-qualified payments table")
        val normalized = query.value.replace(Regex("\\s+"), " ").uppercase()
        assertTrue(normalized.contains("FROM PAYMENTS.TRANSACTIONS"))
        assertTrue(normalized.contains("STATUS NOT IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')"))
        assertTrue(normalized.contains("FOR UPDATE"), "Webhook lookup must serialize with refund/reconciliation")
    }

    @Test
    fun `refund webhook lookup is isolated to refund lifecycle states and locks row`() {
        val method = TransactionRepository::class.java.methods.single {
            it.name == "findRefundByGatewayTransactionId"
        }
        val query = method.getAnnotation(Query::class.java)

        assertNotNull(query)
        assertTrue(query.nativeQuery)
        val normalized = query.value.replace(Regex("\\s+"), " ").uppercase()
        assertTrue(normalized.contains("STATUS IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')"))
        assertTrue(normalized.contains("FOR UPDATE"))
    }
}
