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
    fun `gateway webhook lookup locks row and excludes refund terminal states`() {
        val method = TransactionRepository::class.java.methods.single {
            it.name == "findByGatewayTransactionId"
        }
        val query = method.getAnnotation(Query::class.java)

        assertNotNull(query, "Webhook transaction lookup must remain an explicit financial-state query")
        assertTrue(query.nativeQuery, "Webhook transaction lookup must use the schema-qualified payments table")
        val normalized = query.value.replace(Regex("\\s+"), " ").uppercase()
        assertTrue(normalized.contains("FROM PAYMENTS.TRANSACTIONS"))
        assertTrue(normalized.contains("STATUS NOT IN ('REFUND_PENDING', 'REFUNDED')"))
        assertTrue(normalized.contains("FOR UPDATE"), "Webhook lookup must serialize with refund/reconciliation")
    }
}
