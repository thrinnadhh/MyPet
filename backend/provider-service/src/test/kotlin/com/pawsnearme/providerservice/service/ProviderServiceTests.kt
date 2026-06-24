package com.pawsnearme.providerservice.service

import com.pawsnearme.providerservice.model.*
import com.pawsnearme.providerservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.util.Optional
import java.util.UUID

class ProviderServiceTests {

    private val providerRepository: ProviderRepository = mock()
    private val providerDocumentRepository: ProviderDocumentRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()

    private val providerService = ProviderService(
        providerRepository,
        providerDocumentRepository,
        profileRepository,
        kafkaTemplate
    )

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
}
