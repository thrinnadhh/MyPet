package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.Profile
import com.pawsnearme.providerservice.model.UserRole
import com.pawsnearme.providerservice.repository.ProfileRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import java.util.UUID

class ProfileAdminAccessServiceTests {
    private val profiles: ProfileRepository = mock()
    private val redis: StringRedisTemplate = mock()
    private val values: ValueOperations<String, String> = mock()
    private val outbox: OutboxService = mock()
    private val service = ProfileAdminAccessService(profiles, redis, outbox)

    init {
        whenever(redis.opsForValue()).thenReturn(values)
    }

    @Test
    fun `revoke locks profile records audit event and updates revocation cache`() {
        val actorId = UUID.randomUUID()
        val customer = customerProfile()
        whenever(profiles.findByIdForUpdate(customer.userId)).thenReturn(Optional.of(customer))
        whenever(profiles.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.revoke(customer.userId, actorId, "Repeated payment abuse investigation")

        assertTrue(result.suspended)
        verify(values).set("suspended_user:${customer.userId}", "true")
        verify(outbox).saveEvent(
            eventId = any(),
            aggregateType = eq("USER"),
            aggregateId = eq(customer.userId),
            eventType = eq("CustomerAccessRevoked"),
            eventPayload = any(),
        )
    }

    @Test
    fun `duplicate revoke is idempotent and emits no second audit transition`() {
        val actorId = UUID.randomUUID()
        val customer = customerProfile().apply { suspended = true }
        whenever(profiles.findByIdForUpdate(customer.userId)).thenReturn(Optional.of(customer))

        val result = service.revoke(customer.userId, actorId, "Retrying same support action")

        assertTrue(result.suspended)
        verify(profiles, never()).save(any())
        verify(outbox, never()).saveEvent(any(), any(), any(), any(), any())
        verify(values).set("suspended_user:${customer.userId}", "true")
    }

    @Test
    fun `restore clears revocation cache and records audit event`() {
        val actorId = UUID.randomUUID()
        val customer = customerProfile().apply { suspended = true }
        whenever(profiles.findByIdForUpdate(customer.userId)).thenReturn(Optional.of(customer))
        whenever(profiles.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.restore(customer.userId, actorId, "Support review completed")

        assertFalse(result.suspended)
        verify(redis).delete("suspended_user:${customer.userId}")
        verify(outbox).saveEvent(
            eventId = any(),
            aggregateType = eq("USER"),
            aggregateId = eq(customer.userId),
            eventType = eq("CustomerAccessRestored"),
            eventPayload = any(),
        )
    }

    @Test
    fun `admin target cannot be suspended by customer access controls`() {
        val actorId = UUID.randomUUID()
        val admin = Profile(
            userId = UUID.randomUUID(),
            role = UserRole.ADMIN,
            fullName = "Admin",
            phoneNumber = "9999999999",
        )
        whenever(profiles.findByIdForUpdate(admin.userId)).thenReturn(Optional.of(admin))

        assertThrows<IllegalArgumentException> {
            service.revoke(admin.userId, actorId, "Not a permitted operation")
        }
        verify(profiles, never()).save(any())
        verify(outbox, never()).saveEvent(any(), any(), any(), any(), any())
    }

    private fun customerProfile() = Profile(
        userId = UUID.randomUUID(),
        role = UserRole.CUSTOMER,
        fullName = "Customer",
        phoneNumber = "9876543210",
    )
}
