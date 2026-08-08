package com.pawsnearme.paymentservice.repository

import jakarta.persistence.LockModeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Lock

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
}
