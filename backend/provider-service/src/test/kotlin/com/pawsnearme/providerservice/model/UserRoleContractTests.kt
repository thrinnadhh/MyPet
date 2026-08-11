package com.pawsnearme.providerservice.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class UserRoleContractTests {
    @Test
    fun `canonical platform roles are customer merchant captain and admin only`() {
        val roles = UserRole.entries.map { it.name }

        assertEquals(listOf("CUSTOMER", "MERCHANT", "CAPTAIN", "ADMIN"), roles)
        assertFalse("SUPER_ADMIN" in roles)
        assertFalse("PROVIDER" in roles)
    }
}
