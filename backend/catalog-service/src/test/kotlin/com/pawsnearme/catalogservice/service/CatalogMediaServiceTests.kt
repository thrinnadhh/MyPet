package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.catalogservice.repository.ProviderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class CatalogMediaServiceTests {
    private val offeringRepository: OfferingRepository = mock()
    private val providerRepository: ProviderRepository = mock()
    private val s3Client: S3Client = mock()

    private fun service() = CatalogMediaService(
        offeringRepository = offeringRepository,
        providerRepository = providerRepository,
        s3Client = s3Client,
        bucket = "mypet-catalog-media-test",
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
    fun `merchant owner can upload public catalog image to shared object storage`() {
        val ownerId = UUID.randomUUID()
        val product = offering()
        whenever(offeringRepository.findById(product.offeringId!!)).thenReturn(Optional.of(product))
        whenever(providerRepository.existsByProviderIdAndOwnerUserId(product.providerId, ownerId)).thenReturn(true)
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.arguments[0] as Offering }
        whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())).thenReturn(
            PutObjectResponse.builder().eTag("test-etag").build(),
        )
        val file = MockMultipartFile("file", "food.webp", "image/webp", byteArrayOf(1, 2, 3, 4))

        val stored = service().storeOfferingImage(product.offeringId!!, ownerId, "MERCHANT", file)

        assertTrue(stored.imageUrl.startsWith("https://api.mypet.example/api/v1/catalog/offerings/media/"))
        assertEquals(stored.imageUrl, stored.offering.imageUrl)
        verify(offeringRepository).save(product)
        verify(s3Client).putObject(any<PutObjectRequest>(), any<RequestBody>())
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
    fun `public media lookup rejects path traversal before object storage access`() {
        assertThrows<IllegalArgumentException> {
            service().loadPublicImage("../../secret.jpg")
        }
    }
}
