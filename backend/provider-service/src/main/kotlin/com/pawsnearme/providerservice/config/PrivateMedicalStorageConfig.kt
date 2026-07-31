package com.pawsnearme.providerservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
class PrivateMedicalStorageConfig {
    @Bean(destroyMethod = "close")
    fun medicalReportS3Presigner(
        @Value("\${storage.medical-reports.region:ap-south-1}") region: String,

        @Value("\${storage.medical-reports.access-key:}") accessKey: String,
        @Value("\${storage.medical-reports.secret-key:}") secretKey: String,
    ): S3Presigner {
        val effectiveAccessKey = accessKey.ifBlank { "dev-key" }
        val effectiveSecretKey = secretKey.ifBlank { "dev-secret" }
        val credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(effectiveAccessKey, effectiveSecretKey)
        )


        return S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build()
    }
}
