package com.pawsnearme.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, UUID>
