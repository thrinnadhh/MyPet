package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.DeliveryAddressSnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.orderservice.model.ServiceAreaConfig
import com.pawsnearme.orderservice.repository.ServiceAreaConfigRepository
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class CheckoutLocationPolicyServiceTests {
    private val providerModule: ProviderModuleApi = mock()
    private val serviceAreaRepository: ServiceAreaConfigRepository = mock()
    private val service = CheckoutLocationPolicyService(providerModule, serviceAreaRepository)

    private val customerId = UUID.randomUUID()
    private val addressId = UUID.randomUUID()
    private val address = DeliveryAddressSnapshot(
        addressId = addressId,
        customerId = customerId,
        city = "Tirupati",
        pincode = "517501",
        latitude = 13.6288,
        longitude = 79.4192,
    )

    @Test
    fun `owned address with no pincode override continues to discovery validation`() {
        whenever(providerModule.deliveryAddress(customerId, addressId)).thenReturn(address)
        whenever(serviceAreaRepository.findById("517501")).thenReturn(Optional.empty())

        assertSame(address, service.requireAuthoritativeDeliveryLocation(customerId, addressId))
    }

    @Test
    fun `disabled admin pincode blocks customer checkout`() {
        whenever(providerModule.deliveryAddress(customerId, addressId)).thenReturn(address)
        whenever(serviceAreaRepository.findById("517501")).thenReturn(
            Optional.of(config(enabled = false, deliveryEnabled = true))
        )

        val error = assertThrows<IllegalArgumentException> {
            service.requireAuthoritativeDeliveryLocation(customerId, addressId)
        }
        assert(error.message!!.startsWith("UNSERVICEABLE_REGION"))
    }

    @Test
    fun `delivery disabled admin pincode blocks product delivery`() {
        whenever(providerModule.deliveryAddress(customerId, addressId)).thenReturn(address)
        whenever(serviceAreaRepository.findById("517501")).thenReturn(
            Optional.of(config(enabled = true, deliveryEnabled = false))
        )

        val error = assertThrows<IllegalArgumentException> {
            service.requireAuthoritativeDeliveryLocation(customerId, addressId)
        }
        assert(error.message!!.startsWith("DELIVERY_DISABLED"))
    }

    @Test
    fun `foreign or missing delivery address fails closed before location checks`() {
        whenever(providerModule.deliveryAddress(customerId, addressId)).thenReturn(null)

        val error = assertThrows<IllegalArgumentException> {
            service.requireAuthoritativeDeliveryLocation(customerId, addressId)
        }
        assert(error.message!!.startsWith("DELIVERY_ADDRESS_INVALID"))
    }

    private fun config(enabled: Boolean, deliveryEnabled: Boolean) = ServiceAreaConfig(
        pincode = "517501",
        city = "Tirupati",
        enabled = enabled,
        deliveryEnabled = deliveryEnabled,
        serviceRadiusKm = BigDecimal("25.00"),
        updatedByUserId = UUID.randomUUID(),
    )
}
