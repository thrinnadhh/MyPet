package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ProviderAdminApprovalServiceTests {
    private val providers: ProviderRepository = mock()
    private val outbox: OutboxService = mock()
    private val service = ProviderAdminApprovalService(providers, outbox)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `approval is locked auditable and activates only pending provider`() {
        val provider = provider(ProviderStatus.PENDING_APPROVAL)
        val actorId = UUID.randomUUID()
        whenever(providers.findByIdForUpdate(provider.providerId!!)).thenReturn(Optional.of(provider))
        whenever(providers.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.approve(provider.providerId!!, actorId)

        assertEquals(ProviderStatus.ACTIVE, result.status)
        verify(providers).findByIdForUpdate(provider.providerId!!)
        verify(providers).save(provider)
        verify(outbox).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(provider.providerId!!),
            eventType = eq("ProviderApproved"),
            eventPayload = any(),
        )
    }

    @Test
    fun `approval rejects non pending provider without audit transition`() {
        val provider = provider(ProviderStatus.ACTIVE)
        whenever(providers.findByIdForUpdate(provider.providerId!!)).thenReturn(Optional.of(provider))

        assertThrows<IllegalStateException> {
            service.approve(provider.providerId!!, UUID.randomUUID())
        }
        verify(providers, never()).save(any())
        verify(outbox, never()).saveEvent(any(), any(), any(), any(), any())
    }

    private fun provider(status: ProviderStatus) = Provider(
        providerId = UUID.randomUUID(),
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Happy Tails",
        addressLine = "12 Main Road",
        city = "Tirupati",
        pincode = "517501",
        geoLocation = geometryFactory.createPoint(Coordinate(79.4192, 13.6288)),
        status = status,
        commissionPct = BigDecimal("15.00"),
    )
}
