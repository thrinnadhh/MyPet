package com.pawsnearme.providerservice.service

import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.providerservice.model.*
import com.pawsnearme.providerservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ProviderServiceTests {
    private val providerRepository: ProviderRepository = mock()
    private val providerDocumentRepository: ProviderDocumentRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val userRoleJoinRepository: UserRoleJoinRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val outboxService: OutboxService = mock()

    private val providerService = ProviderService(
        providerRepository,
        providerDocumentRepository,
        profileRepository,
        userRoleJoinRepository,
        kafkaTemplate,
        outboxService
    )

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `syncAuthenticatedProfile creates a customer profile when role is missing`() {
        val userId = UUID.randomUUID()
        whenever(profileRepository.findById(userId)).thenReturn(Optional.empty())
        whenever(profileRepository.save(any())).thenAnswer { it.arguments[0] as Profile }
        whenever(userRoleJoinRepository.save(any())).thenAnswer { it.arguments[0] as UserRoleJoin }

        val profile = providerService.syncAuthenticatedProfile(
            userId = userId,
            role = null,
            email = "customer@example.com",
            fullName = null,
            phoneNumber = null,
            avatarUrl = null
        )

        assertEquals(UserRole.CUSTOMER, profile.role)
        assertEquals("customer", profile.fullName)
        verify(userRoleJoinRepository).save(UserRoleJoin(UserRoleKey(userId, UserRole.CUSTOMER)))
    }

    @Test
    fun `syncAuthenticatedProfile maps provider role to merchant`() {
        val userId = UUID.randomUUID()
        whenever(profileRepository.findById(userId)).thenReturn(Optional.empty())
        whenever(profileRepository.save(any())).thenAnswer { it.arguments[0] as Profile }
        whenever(userRoleJoinRepository.save(any())).thenAnswer { it.arguments[0] as UserRoleJoin }

        val profile = providerService.syncAuthenticatedProfile(
            userId = userId,
            role = "PROVIDER",
            email = "merchant@example.com",
            fullName = "Merchant",
            phoneNumber = "9999999999",
            avatarUrl = null
        )

        assertEquals(UserRole.MERCHANT, profile.role)
    }

    @Test
    fun `createProvider should create PET_STORE with DELIVERY fulfillment`() {
        // Arrange
        val ownerId = UUID.randomUUID()
        val merchantProfile = Profile(
            userId = ownerId,
            role = UserRole.MERCHANT,
            fullName = "John Merchant",
            phoneNumber = "1234567890"
        )
        whenever(profileRepository.findById(ownerId)).thenReturn(Optional.of(merchantProfile))
        whenever(providerRepository.save(any<Provider>())).thenAnswer { invocation ->
            val p = invocation.getArgument<Provider>(0)
            p.apply { providerId = UUID.randomUUID() }
        }

        // Act
        val provider = providerService.createProvider(
            ownerUserId = ownerId,
            providerType = ProviderType.PET_STORE,
            fulfillmentType = FulfillmentType.DELIVERY,
            name = "Happy Pets Store",
            description = "Best pet store",
            licenseNumber = null,
            licenseDocUrl = null,
            addressLine = "123 Main St",
            city = "City",
            pincode = "123456",
            longitude = 12.34,
            latitude = 56.78
        )

        // Assert
        assertNotNull(provider.providerId)
        assertEquals(ProviderType.PET_STORE, provider.providerType)
        assertEquals(FulfillmentType.DELIVERY, provider.fulfillmentType)
        assertEquals(ProviderStatus.DRAFT, provider.status)
        verify(providerRepository).save(any<Provider>())
    }

    @Test
    fun `createProvider should reject PET_STORE with APPOINTMENT fulfillment`() {
        // Arrange
        val ownerId = UUID.randomUUID()
        val merchantProfile = Profile(
            userId = ownerId,
            role = UserRole.MERCHANT,
            fullName = "John Merchant",
            phoneNumber = "1234567890"
        )
        whenever(profileRepository.findById(ownerId)).thenReturn(Optional.of(merchantProfile))

        // Act & Assert
        val exception = assertThrows<IllegalArgumentException> {
            providerService.createProvider(
                ownerUserId = ownerId,
                providerType = ProviderType.PET_STORE,
                fulfillmentType = FulfillmentType.APPOINTMENT,
                name = "Happy Pets Store",
                description = "Best pet store",
                licenseNumber = null,
                licenseDocUrl = null,
                addressLine = "123 Main St",
                city = "City",
                pincode = "123456",
                longitude = 12.34,
                latitude = 56.78
            )
        }
        assertTrue(exception.message!!.contains("Invalid combination"))
        verify(providerRepository, never()).save(any<Provider>())
    }

    @Test
    fun `createProvider should require license for VET_HOSPITAL`() {
        // Arrange
        val ownerId = UUID.randomUUID()
        val merchantProfile = Profile(
            userId = ownerId,
            role = UserRole.MERCHANT,
            fullName = "John Merchant",
            phoneNumber = "1234567890"
        )
        whenever(profileRepository.findById(ownerId)).thenReturn(Optional.of(merchantProfile))

        // Act & Assert
        val exception = assertThrows<IllegalArgumentException> {
            providerService.createProvider(
                ownerUserId = ownerId,
                providerType = ProviderType.VET_HOSPITAL,
                fulfillmentType = FulfillmentType.APPOINTMENT,
                name = "Happy Vet Clinic",
                description = "Vet Clinic",
                licenseNumber = "", // Empty!
                licenseDocUrl = null,
                addressLine = "123 Vet St",
                city = "City",
                pincode = "123456",
                longitude = 12.34,
                latitude = 56.78
            )
        }
        assertTrue(exception.message!!.contains("license number is required for VET_HOSPITAL"))
    }

    @Test
    fun `updateCommission persists rounded commission and publishes event`() {
        val providerId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val provider = sampleProvider(providerId)
        whenever(providerRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider))
        whenever(providerRepository.save(any())).thenAnswer { it.arguments[0] as Provider }

        val result = providerService.updateCommission(
            providerId,
            BigDecimal("18.456"),
            actorId,
            "Launch promo margin review"
        )

        assertEquals(BigDecimal("18.46"), result.commissionPct)
        verify(providerRepository).save(provider)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("PROVIDER"),
            aggregateId = eq(providerId),
            eventType = eq("ProviderCommissionUpdated"),
            eventPayload = any()
        )
    }

    @Test
    fun `updateCommission rejects commission below zero`() {
        val exception = assertThrows<IllegalArgumentException> {
            providerService.updateCommission(UUID.randomUUID(), BigDecimal("-0.01"), UUID.randomUUID(), null)
        }

        assertTrue(exception.message!!.contains("between 0 and 50"))
        verify(providerRepository, never()).save(any())
    }

    @Test
    fun `updateCommission rejects commission above fifty`() {
        val exception = assertThrows<IllegalArgumentException> {
            providerService.updateCommission(UUID.randomUUID(), BigDecimal("50.01"), UUID.randomUUID(), null)
        }

        assertTrue(exception.message!!.contains("between 0 and 50"))
        verify(providerRepository, never()).save(any())
    }

    private fun sampleProvider(providerId: UUID) = Provider(
        providerId = providerId,
        ownerUserId = UUID.randomUUID(),
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Happy Tails",
        addressLine = "12 Main Road",
        city = "Bengaluru",
        pincode = "560001",
        geoLocation = geometryFactory.createPoint(Coordinate(77.5946, 12.9716)),
        status = ProviderStatus.ACTIVE,
        commissionPct = BigDecimal("15.00")
    )
}
