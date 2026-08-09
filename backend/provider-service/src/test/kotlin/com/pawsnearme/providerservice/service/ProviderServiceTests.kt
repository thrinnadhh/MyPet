package com.pawsnearme.providerservice.service

import com.pawsnearme.providerservice.model.*
import com.pawsnearme.providerservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.eq
import org.mockito.kotlin.argThat
import org.springframework.kafka.core.KafkaTemplate
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ProviderServiceTests {

    private val providerRepository: ProviderRepository = mock()
    private val providerDocumentRepository: ProviderDocumentRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val userRoleJoinRepository: UserRoleJoinRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()

    private val outboxService: com.pawsnearme.common.outbox.OutboxService = mock()

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
    fun `createProvider with non-MERCHANT role should throw IllegalArgumentException`() {
        // Arrange
        val ownerId = UUID.randomUUID()
        val customerProfile = Profile(
            userId = ownerId,
            role = UserRole.CUSTOMER,
            fullName = "John Customer",
            phoneNumber = "1234567890"
        )
        whenever(profileRepository.findById(ownerId)).thenReturn(Optional.of(customerProfile))

        // Act & Assert
        val exception = assertThrows<IllegalArgumentException> {
            providerService.createProvider(
                ownerUserId = ownerId,
                providerType = ProviderType.PET_STORE,
                fulfillmentType = FulfillmentType.DELIVERY,
                name = "My Pet Store",
                description = "Description",
                licenseNumber = null,
                licenseDocUrl = null,
                addressLine = "123 Main St",
                city = "City",
                pincode = "123456",
                longitude = 12.34,
                latitude = 56.78
            )
        }
        assertTrue(exception.message!!.contains("User must have MERCHANT role"))
    }

    @Test
    fun `createProvider with mismatching fulfillment type should throw IllegalArgumentException`() {
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
                fulfillmentType = FulfillmentType.APPOINTMENT, // Mismatch! PET_STORE requires DELIVERY
                name = "My Pet Store",
                description = "Description",
                licenseNumber = null,
                licenseDocUrl = null,
                addressLine = "123 Main St",
                city = "City",
                pincode = "123456",
                longitude = 12.34,
                latitude = 56.78
            )
        }
        assertTrue(exception.message!!.contains("does not support FulfillmentType"))
    }

    @Test
    fun `createProvider with VET_HOSPITAL and missing license number should throw IllegalArgumentException`() {
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

    @Test
    fun `syncAuthenticatedProfile creates profile and maps provider role to merchant`() {
        val userId = UUID.randomUUID()
        whenever(profileRepository.findById(userId)).thenReturn(Optional.empty())
        whenever(profileRepository.save(any())).thenAnswer { it.arguments[0] as Profile }
        whenever(userRoleJoinRepository.save(any())).thenAnswer { it.arguments[0] as UserRoleJoin }

        val result = providerService.syncAuthenticatedProfile(
            userId = userId,
            role = "PROVIDER",
            email = "merchant@example.com",
            fullName = "Merchant Owner",
            phoneNumber = "+919999000001",
            avatarUrl = null
        )

        assertEquals(userId, result.userId)
        assertEquals(UserRole.MERCHANT, result.role)
        assertEquals("Merchant Owner", result.fullName)
        assertEquals("+919999000001", result.phoneNumber)
        verify(profileRepository).save(any())
        verify(userRoleJoinRepository).save(argThat { id.userId == userId && id.role == UserRole.MERCHANT })
    }

    @Test
    fun `syncAuthenticatedProfile updates existing profile role without overwriting blank metadata`() {
        val userId = UUID.randomUUID()
        val existing = Profile(
            userId = userId,
            role = UserRole.CUSTOMER,
            fullName = "Existing Name",
            phoneNumber = "+919999000002"
        )
        whenever(profileRepository.findById(userId)).thenReturn(Optional.of(existing))
        whenever(profileRepository.save(any())).thenAnswer { it.arguments[0] as Profile }
        whenever(userRoleJoinRepository.save(any())).thenAnswer { it.arguments[0] as UserRoleJoin }

        val result = providerService.syncAuthenticatedProfile(
            userId = userId,
            role = "ADMIN",
            email = null,
            fullName = "",
            phoneNumber = "",
            avatarUrl = null
        )

        assertEquals(UserRole.ADMIN, result.role)
        assertEquals("Existing Name", result.fullName)
        assertEquals("+919999000002", result.phoneNumber)
        verify(userRoleJoinRepository).save(argThat { id.userId == userId && id.role == UserRole.ADMIN })
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
