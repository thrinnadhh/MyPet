package com.pawsnearme.providerservice.controller

import com.pawsnearme.providerservice.model.Address
import com.pawsnearme.providerservice.model.FulfillmentType
import com.pawsnearme.providerservice.model.Pet
import com.pawsnearme.providerservice.model.Profile
import com.pawsnearme.providerservice.model.Provider
import com.pawsnearme.providerservice.model.ProviderStatus
import com.pawsnearme.providerservice.model.ProviderType
import com.pawsnearme.providerservice.model.UserRole
import com.pawsnearme.providerservice.repository.AddressRepository
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.ProfileRepository
import com.pawsnearme.providerservice.repository.ProviderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class InternalProviderControllerTests {
    private val repository: ProviderRepository = mock()
    private val profileRepository: ProfileRepository = mock()
    private val petRepository: PetRepository = mock()
    private val addressRepository: AddressRepository = mock()
    private val controller = InternalProviderController(
        repository,
        profileRepository,
        petRepository,
        addressRepository,
        "test-internal-secret",
    )

    @Test
    fun `owner lookup requires the internal service secret`() {
        assertThrows<ProviderAccessDeniedException> {
            controller.getProviderOwner(UUID.randomUUID(), "wrong-secret")
        }
    }

    @Test
    fun `owner lookup returns only internal ownership data`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val provider = provider(providerId, ownerId, ProviderStatus.ACTIVE)
        whenever(repository.findById(providerId)).thenReturn(Optional.of(provider))

        val response = controller.getProviderOwner(providerId, "test-internal-secret")

        assertEquals(providerId, response.body?.providerId)
        assertEquals(ownerId, response.body?.ownerUserId)
    }

    @Test
    fun `operational state is true only for active provider`() {
        val activeId = UUID.randomUUID()
        val inactiveId = UUID.randomUUID()
        whenever(repository.findById(activeId)).thenReturn(Optional.of(provider(activeId, UUID.randomUUID(), ProviderStatus.ACTIVE)))
        whenever(repository.findById(inactiveId)).thenReturn(Optional.of(provider(inactiveId, UUID.randomUUID(), ProviderStatus.PENDING_APPROVAL)))

        assertTrue(controller.getProviderOperationalState(activeId, "test-internal-secret").body!!.operational)
        assertFalse(controller.getProviderOperationalState(inactiveId, "test-internal-secret").body!!.operational)
    }

    @Test
    fun `customer delivery address requires ownership and internal secret`() {
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val address = Address(
            addressId = addressId,
            userId = customerId,
            label = "Home",
            line1 = "Temple Road",
            city = "Tirupati",
            state = "Andhra Pradesh",
            pincode = "517501",
            geoLat = BigDecimal("13.6288"),
            geoLng = BigDecimal("79.4192"),
            isDefault = true,
        )
        whenever(addressRepository.findById(addressId)).thenReturn(Optional.of(address))

        assertThrows<ProviderAccessDeniedException> {
            controller.getCustomerDeliveryAddress(customerId, addressId, "wrong-secret")
        }
        val response = controller.getCustomerDeliveryAddress(customerId, addressId, "test-internal-secret").body!!
        assertEquals("Tirupati", response.city)
        assertEquals("517501", response.pincode)
    }

    @Test
    fun `customer delivery address rejects another customers address`() {
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        whenever(addressRepository.findById(addressId)).thenReturn(
            Optional.of(
                Address(
                    addressId = addressId,
                    userId = UUID.randomUUID(),
                    line1 = "Other street",
                    city = "Tirupati",
                    state = "Andhra Pradesh",
                    pincode = "517501",
                    geoLat = BigDecimal("13.6288"),
                    geoLng = BigDecimal("79.4192"),
                )
            )
        )
        assertThrows<ProviderAccessDeniedException> {
            controller.getCustomerDeliveryAddress(customerId, addressId, "test-internal-secret")
        }
    }

    @Test
    fun `customer pet identity requires ownership and internal secret`() {
        val customerId = UUID.randomUUID()
        val petId = UUID.randomUUID()
        val profile = Profile(
            userId = customerId,
            role = UserRole.CUSTOMER,
            fullName = "Ananya Rao",
            phoneNumber = "+919876543210",
        )
        val pet = Pet(petId = petId, ownerId = customerId, name = "Bruno")
        whenever(profileRepository.findById(customerId)).thenReturn(Optional.of(profile))
        whenever(petRepository.findById(petId)).thenReturn(Optional.of(pet))

        assertThrows<ProviderAccessDeniedException> {
            controller.getCustomerPetIdentity(customerId, petId, "wrong-secret")
        }

        val response = controller.getCustomerPetIdentity(customerId, petId, "test-internal-secret")
        assertEquals("Ananya Rao", response.body?.customerName)
        assertEquals("Bruno", response.body?.petName)
    }

    @Test
    fun `customer pet identity rejects a pet owned by another customer`() {
        val customerId = UUID.randomUUID()
        val petId = UUID.randomUUID()
        whenever(profileRepository.findById(customerId)).thenReturn(
            Optional.of(Profile(customerId, UserRole.CUSTOMER, "Customer", "+919999999999"))
        )
        whenever(petRepository.findById(petId)).thenReturn(
            Optional.of(Pet(petId = petId, ownerId = UUID.randomUUID(), name = "Other pet"))
        )

        assertThrows<ProviderAccessDeniedException> {
            controller.getCustomerPetIdentity(customerId, petId, "test-internal-secret")
        }
    }

    private fun provider(id: UUID, ownerId: UUID, status: ProviderStatus) = Provider(
        providerId = id,
        ownerUserId = ownerId,
        providerType = ProviderType.PET_STORE,
        fulfillmentType = FulfillmentType.DELIVERY,
        name = "Internal Store",
        addressLine = "Private address",
        city = "Tirupati",
        pincode = "517501",
        geoLocation = GeometryFactory().createPoint(Coordinate(79.4192, 13.6288)),
        status = status,
    )
}