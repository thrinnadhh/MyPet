package com.pawsnearme.notificationservice.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class DevicePushTokenRepositoryTests {
    @Test
    fun `push token delete is an explicit transactional bulk operation`() {
        val method = DevicePushTokenRepository::class.java.getMethod(
            "deleteByUserIdAndExpoPushToken",
            UUID::class.java,
            String::class.java
        )

        assertNotNull(method.getAnnotation(Transactional::class.java))
        val modifying = method.getAnnotation(Modifying::class.java)
        assertNotNull(modifying)
        assertTrue(modifying.clearAutomatically)
        assertTrue(modifying.flushAutomatically)

        val query = method.getAnnotation(Query::class.java)
        assertNotNull(query)
        assertTrue(query.value.contains("DELETE FROM DevicePushToken"))
        assertEquals(Int::class.javaPrimitiveType, method.returnType)
    }
}
