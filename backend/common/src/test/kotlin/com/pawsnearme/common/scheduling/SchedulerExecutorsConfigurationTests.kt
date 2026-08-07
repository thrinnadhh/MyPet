package com.pawsnearme.common.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SchedulerExecutorsConfigurationTests {
    private val configuration = SchedulerExecutorsConfiguration()

    @Test
    fun `general scheduler uses configured bounded pool`() {
        val scheduler = configuration.taskScheduler(6)

        assertEquals(6, scheduler.poolSize)
        assertEquals("mypet-scheduler-", scheduler.threadNamePrefix)
    }

    @Test
    fun `outbox scheduler is isolated and clamps invalid pool sizes`() {
        val scheduler = configuration.outboxTaskScheduler(0)

        assertEquals(1, scheduler.poolSize)
        assertEquals("mypet-outbox-", scheduler.threadNamePrefix)
    }
}
