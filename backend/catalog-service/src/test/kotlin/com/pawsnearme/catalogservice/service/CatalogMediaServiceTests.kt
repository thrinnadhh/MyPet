package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.catalogservice.repository.ProviderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.nio.file.Path
import java.util.Optional
import java.util.UUID

class CatalogMediaServiceTests {
    @TempDir
    lateinit var tempDir: Path

    private val offeringRepository: OfferingRepository = mock()
    private val providerRepository: ProviderRepository = mock()

    private fun service() = CatalogMediaService(
        offeringRepository = offeringRepository,
        providerRepository = providerRepository,
        mediaDir = tempDir.toString(),
        publicBaseUrl = "https://api.mypet.example",
    )

    private fun offering(providerId: UUID = UUID.randomUUID()) = Offering(
        offeringId = UUID.randomUUID(),
        providerId = providerId,
        name = "Adult dog food",
        price = BigDecimal("399.00"),
        status = OfferingStatus.ACTIVE,
        stockQuantity = 10,
        barcode = "8901234567890",
    )

    @Test
    fun `merchant owner can upload public catalog image`() {
        val ownerId = UUID.randomUUID()
        val product = offering()
        whenever(offeringRepository.findById(product.offeringId!!)).thenReturn(Optional.of(product))
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(product.providerId, ownerId)).thenReturn(true)
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.arguments[0] as Offering }
        val file = MockMultipartFile("file", "food.webp", "image/webp", byteArrayOf(1, 2, 3, 4))

        val stored = service().storeOfferingImage(product.offeringId!!, ownerId, "MERCHANT", file)

        assertTrue(stored.imageUrl.startsWith("https://api.mypet.example/api/v1/catalog/offerings/media/"))
        assertEquals(stored.imageUrl, stored.offering.imageUrl)
        verify(offeringRepository).save(product)
        val filename = stored.imageUrl.substringAfterLast('/')
        assertTrue(tempDir.resolve(filename).toFile().isFile)
    }

    @Test
    fun `different merchant cannot replace offering image`() {
        val requesterId = UUID.randomUUID()
        val product = offering()
        whenever(offeringRepository.findById(product.offeringId!!)).thenReturn(Optional.of(product))
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(product.providerId, requesterId)).thenReturn(false)
        val file = MockMultipartFile("file", "food.jpg", "image/jpeg", byteArrayOf(1))

        assertThrows<CatalogMediaAccessDeniedException> {
            service().storeOfferingImage(product.offeringId!!, requesterId, "MERCHANT", file)
        }
    }

    @Test
    fun `non image media is rejected`() {
        val ownerId = UUID.randomUUID()
        val product = offering()
        whenever(offeringRepository.findById(product.offeringId!!)).thenReturn(Optional.of(product))
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(product.providerId, ownerId)).thenReturn(true)
        val file = MockMultipartFile("file", "payload.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        val error = assertThrows<IllegalArgumentException> {
            service().storeOfferingImage(product.offeringId!!, ownerId, "MERCHANT", file)
        }
        assertTrue(error.message!!.contains("JPEG, PNG and WebP"))
    }

    @Test
    fun `public media lookup rejects path traversal`() {
        assertThrows<IllegalArgumentException> {
            service().loadPublicImage("../../secret.jpg")
        }
    }
}
