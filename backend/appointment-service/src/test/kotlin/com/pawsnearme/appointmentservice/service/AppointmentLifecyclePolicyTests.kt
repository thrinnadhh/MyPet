package com.pawsnearme.appointmentservice.service

import com.pawsnearme.appointmentservice.model.AppointmentStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class AppointmentLifecyclePolicyTests {
    private val policy = AppointmentLifecyclePolicy()

    @Test
    fun `merchant can confirm paid appointment and finish confirmed appointment`() {
        assertDoesNotThrow {
            policy.requireAllowed(AppointmentStatus.PAID, AppointmentStatus.CONFIRMED, "MERCHANT", false)
        }
        listOf(
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW
        ).forEach { target ->
            assertDoesNotThrow {
                policy.requireAllowed(AppointmentStatus.CONFIRMED, target, "MERCHANT", false)
            }
        }
    }

    @Test
    fun `merchant cannot skip payment state for a held online appointment`() {
        assertThrows<AppointmentAccessDeniedException> {
            policy.requireAllowed(
                AppointmentStatus.SLOT_HELD,
                AppointmentStatus.CONFIRMED,
                "MERCHANT",
                false
            )
        }
    }

    @Test
    fun `merchant cannot fabricate held or expired state`() {
        assertThrows<AppointmentAccessDeniedException> {
            policy.requireAllowed(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.EXPIRED,
                "MERCHANT",
                false
            )
        }
        assertThrows<AppointmentAccessDeniedException> {
            policy.requireAllowed(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.SLOT_HELD,
                "MERCHANT",
                false
            )
        }
    }

    @Test
    fun `customer can cancel held paid or confirmed appointments but cannot complete them`() {
        listOf(AppointmentStatus.SLOT_HELD, AppointmentStatus.PAID, AppointmentStatus.CONFIRMED).forEach { current ->
            assertDoesNotThrow {
                policy.requireAllowed(current, AppointmentStatus.CANCELLED, "CUSTOMER", true)
            }
        }
        assertThrows<AppointmentAccessDeniedException> {
            policy.requireAllowed(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.COMPLETED,
                "CUSTOMER",
                true
            )
        }
    }

    @Test
    fun `terminal appointments reject additional transitions`() {
        assertThrows<IllegalStateException> {
            policy.requireAllowed(
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                "ADMIN",
                false
            )
        }
    }

    @Test
    fun `repeating current status is idempotent`() {
        assertDoesNotThrow {
            policy.requireAllowed(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CONFIRMED,
                "MERCHANT",
                false
            )
        }
    }
}
