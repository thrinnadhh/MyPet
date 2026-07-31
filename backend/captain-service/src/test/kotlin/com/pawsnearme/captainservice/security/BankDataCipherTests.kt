package com.pawsnearme.captainservice.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BankDataCipherTests {
    private val cipher = BankDataCipher(
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    )

    @Test
    fun `encrypt and decrypt round trip`() {
        val encrypted = requireNotNull(cipher.encrypt("123456789012"))

        assertTrue(encrypted.startsWith("v1:"))
        assertEquals("123456789012", cipher.decrypt(encrypted))
    }

    @Test
    fun `same value uses a fresh random IV`() {
        val first = cipher.encrypt("HDFC0001234")
        val second = cipher.encrypt("HDFC0001234")

        assertNotEquals(first, second)
        assertEquals("HDFC0001234", cipher.decrypt(first))
        assertEquals("HDFC0001234", cipher.decrypt(second))
    }

    @Test
    fun `legacy plaintext remains readable during migration`() {
        assertEquals("legacy-value", cipher.decrypt("legacy-value"))
    }
}
