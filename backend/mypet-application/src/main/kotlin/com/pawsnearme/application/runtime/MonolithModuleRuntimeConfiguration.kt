package com.pawsnearme.application.runtime

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.pawsnearme.common.outbox.OutboxEventPublisherFactory
import com.pawsnearme.common.outbox.OutboxPersistence
import com.pawsnearme.common.outbox.OutboxPoller
import com.pawsnearme.common.scheduling.SchedulerLockProviderFactory
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import javax.sql.DataSource

/**
 * Activates all MyPet bounded contexts inside one Spring Boot process.
 *
 * Legacy service boot classes, per-service infrastructure factories, outbox
 * pollers, ShedLock providers, gateway-trust filters and HTTP fallback adapters
 * are deliberately excluded. Public controllers and business components remain
 * unchanged, while typed module facades satisfy all in-process collaboration
 * contracts.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "mypet.runtime",
    name = ["modules-enabled"],
    havingValue = "true",
)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@ConfigurationPropertiesScan(
    basePackages = [
        "com.pawsnearme.providerservice",
        "com.pawsnearme.catalogservice",
        "com.pawsnearme.discoveryservice",
        "com.pawsnearme.orderservice",
        "com.pawsnearme.appointmentservice",
        "com.pawsnearme.dispatchservice",
        "com.pawsnearme.captainservice",
        "com.pawsnearme.notificationservice",
        "com.pawsnearme.reviewservice",
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.chatservice",
        "com.pawsnearme.contentservice",
    ],
)
@EntityScan(
    basePackages = [
        "com.pawsnearme.providerservice",
        "com.pawsnearme.catalogservice",
        "com.pawsnearme.discoveryservice",
        "com.pawsnearme.orderservice",
        "com.pawsnearme.appointmentservice",
        "com.pawsnearme.dispatchservice",
        "com.pawsnearme.captainservice",
        "com.pawsnearme.notificationservice",
        "com.pawsnearme.reviewservice",
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.chatservice",
        "com.pawsnearme.contentservice",
        "com.pawsnearme.common",
    ],
)
@EnableJpaRepositories(
    basePackages = [
        "com.pawsnearme.providerservice",
        "com.pawsnearme.catalogservice",
        "com.pawsnearme.discoveryservice",
        "com.pawsnearme.orderservice",
        "com.pawsnearme.appointmentservice",
        "com.pawsnearme.dispatchservice",
        "com.pawsnearme.captainservice",
        "com.pawsnearme.notificationservice",
        "com.pawsnearme.reviewservice",
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.chatservice",
        "com.pawsnearme.contentservice",
        "com.pawsnearme.common.idempotency",
        "com.pawsnearme.common.outbox",
    ],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
@ComponentScan(
    basePackages = [
        "com.pawsnearme.providerservice",
        "com.pawsnearme.catalogservice",
        "com.pawsnearme.discoveryservice",
        "com.pawsnearme.orderservice",
        "com.pawsnearme.appointmentservice",
        "com.pawsnearme.dispatchservice",
        "com.pawsnearme.captainservice",
        "com.pawsnearme.notificationservice",
        "com.pawsnearme.reviewservice",
        "com.pawsnearme.paymentservice",
        "com.pawsnearme.chatservice",
        "com.pawsnearme.contentservice",
        "com.pawsnearme.common",
    ],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = [SpringBootConfiguration::class],
        ),
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.pawsnearme\\..*\\.config\\.(OutboxConfig|ShedLockConfig)"],
        ),
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.pawsnearme\\.(orderservice|paymentservice)\\.config\\.InfraConfig"],
        ),
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.pawsnearme\\..*\\.module\\..*RemoteModuleConfiguration"],
        ),
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.pawsnearme\\.common\\.security\\..*"],
        ),
    ],
)
class MonolithModuleRuntimeConfiguration {

    /** One HTTP client replaces duplicated service-local infrastructure beans. */
    @Bean
    @Primary
    fun monolithRestOperations(): RestOperations {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3_000)
            setReadTimeout(10_000)
        }
        return RestTemplate(factory)
    }

    /** One Jackson configuration is shared by every bounded context. */
    @Bean
    @Primary
    fun monolithObjectMapper(): ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    /** One durable poller drains every schema-owned outbox through the primary adapter. */
    @Bean
    fun monolithOutboxPoller(
        outboxPersistence: OutboxPersistence,
        kafkaTemplate: KafkaTemplate<String, Any>,
        objectMapper: ObjectMapper,
        applicationEventPublisher: ApplicationEventPublisher,
        @Value("\${mypet.events.delivery-mode:KAFKA_ONLY}") deliveryMode: String,
    ): OutboxPoller = OutboxPoller(
        outboxPersistence,
        OutboxEventPublisherFactory.create(
            kafkaTemplate,
            objectMapper,
            applicationEventPublisher,
            deliveryMode,
        ),
    )

    /** All scheduled jobs coordinate through one schema-qualified lock table. */
    @Bean
    fun monolithLockProvider(
        dataSource: DataSource,
        @Value("\${mypet.scheduling.lock-table:orders.shedlock}") tableName: String,
    ): LockProvider = SchedulerLockProviderFactory.create(dataSource, tableName)

    @Bean
    fun monolithRuntimeInfoContributor(): InfoContributor = InfoContributor { builder ->
        builder.withDetail(
            "monolithRuntime",
            linkedMapOf(
                "enabled" to true,
                "deploymentUnits" to 1,
                "embeddedEdge" to true,
                "moduleCommunication" to "in-process-typed-interfaces",
                "databasePools" to 1,
                "objectMappers" to 1,
                "httpClients" to 1,
                "outboxPublishers" to 1,
                "outboxSchemas" to MonolithOutboxOwnerRegistry.schemas.size,
                "lockProviders" to 1,
                "legacyServiceContainersRequired" to false,
            ),
        )
    }
}
