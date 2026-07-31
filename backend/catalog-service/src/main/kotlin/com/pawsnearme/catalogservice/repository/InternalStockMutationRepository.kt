package com.pawsnearme.catalogservice.repository

import com.pawsnearme.catalogservice.model.InternalStockMutation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InternalStockMutationRepository : JpaRepository<InternalStockMutation, UUID>
