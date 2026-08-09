package com.pawsnearme.catalogservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class CatalogMediaStorageConfig {
    @Bean(destroyMethod = "close")
    fun catalogMediaS3Client(
        @Value("\${storage.catalog-media.region:ap-south-1}") region: String,
        @Value("\${storage.catalog-media.access-key:}") accessKey: String,
        @Value("\${storage.catalog-media.secret-key:}") secretKey: String,
    ): S3Client {
        val credentialsProvider = if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        } else {
            DefaultCredentialsProvider.create()
        }

        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build()
    }
}
