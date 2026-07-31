package com.pawsnearme.providerservice.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
class MedicalReportStorageService(
    private val presigner: S3Presigner,
    @Value("\${storage.medical-reports.bucket:pawsnearme-private-reports}") private val bucket: String,

    @Value("\${storage.medical-reports.url-ttl-seconds:900}") private val urlTtlSeconds: Long,
) {
    fun validateObjectKey(ownerId: UUID, petId: UUID, objectKey: String) {
        val expectedPrefix = "medical-reports/$ownerId/$petId/"
        require(objectKey.startsWith(expectedPrefix)) {
            "Medical report object key must be scoped to the authenticated owner and pet"
        }
        require(objectKey.length <= 1024 && !objectKey.contains("..") && !objectKey.contains('\\')) {
            "Medical report object key is invalid"
        }
    }

    fun createDownloadUrl(ownerId: UUID, petId: UUID, objectKey: String): String {
        validateObjectKey(ownerId, petId, objectKey)
        val ttl = urlTtlSeconds.coerceIn(60, 3600)
        val objectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(ttl))
            .getObjectRequest(objectRequest)
            .build()
        return presigner.presignGetObject(presignRequest).url().toExternalForm()
    }
}
