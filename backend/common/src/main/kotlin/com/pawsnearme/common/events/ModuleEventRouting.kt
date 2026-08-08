package com.pawsnearme.common.events

import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

enum class WorkflowExecutionKind {
    DIRECT_CALL,
    IN_PROCESS_EVENT,
    DURABLE_OUTBOX_JOB
}

data class WorkflowRoute(
    val id: String,
    val producerModule: String,
    val consumerModules: Set<String>,
    val executionKind: WorkflowExecutionKind,
    val topic: String? = null,
    val eventTypes: Set<String> = emptySet(),
    val inProcessReplacementReady: Boolean = false,
    val kafkaRollbackRetained: Boolean = true
)

class WorkflowCatalog(routes: Collection<WorkflowRoute>) {
    val routes: List<WorkflowRoute> = routes.sortedBy(WorkflowRoute::id)

    init {
        require(this.routes.isNotEmpty()) { "At least one workflow route is required" }
        require(this.routes.map(WorkflowRoute::id).distinct().size == this.routes.size) {
            "Workflow route ids must be unique"
        }
        require(this.routes.all { it.id.matches(Regex("[a-z][a-z0-9.-]*")) }) {
            "Workflow route ids must be stable lowercase identifiers"
        }
        require(this.routes.filter { it.executionKind != WorkflowExecutionKind.DIRECT_CALL }.all { !it.topic.isNullOrBlank() }) {
            "Event and durable-job routes require a topic"
        }
        require(this.routes.filter { it.executionKind == WorkflowExecutionKind.DIRECT_CALL }.all { it.topic == null }) {
            "Direct-call routes must not declare a transport topic"
        }
    }

    fun routesForTopic(topic: String): List<WorkflowRoute> = routes.filter { it.topic == topic }

    fun hasInProcessReplacement(topic: String): Boolean =
        routesForTopic(topic).any(WorkflowRoute::inProcessReplacementReady)

    fun count(kind: WorkflowExecutionKind): Int = routes.count { it.executionKind == kind }
}

object MyPetWorkflowCatalog {
    fun create(): WorkflowCatalog = WorkflowCatalog(
        listOf(
            WorkflowRoute(
                id = "catalog.stock-consistency",
                producerModule = "order",
                consumerModules = setOf("catalog"),
                executionKind = WorkflowExecutionKind.DIRECT_CALL,
                kafkaRollbackRetained = false
            ),
            WorkflowRoute(
                id = "payment.order-consistency",
                producerModule = "order",
                consumerModules = setOf("payment"),
                executionKind = WorkflowExecutionKind.DIRECT_CALL,
                kafkaRollbackRetained = false
            ),
            WorkflowRoute(
                id = "provider.ownership-consistency",
                producerModule = "appointment",
                consumerModules = setOf("provider"),
                executionKind = WorkflowExecutionKind.DIRECT_CALL,
                kafkaRollbackRetained = false
            ),
            WorkflowRoute(
                id = "order.lifecycle-fanout",
                producerModule = "order",
                consumerModules = setOf("notification", "dispatch", "captain"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "orders.events",
                eventTypes = setOf("OrderPlaced", "OrderStatusChanged", "OrderCancelled"),
                inProcessReplacementReady = true
            ),
            WorkflowRoute(
                id = "appointment.reminder-scheduling",
                producerModule = "appointment",
                consumerModules = setOf("notification"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "appointments.events",
                eventTypes = setOf("AppointmentBooked", "AppointmentStatusChanged"),
                inProcessReplacementReady = true
            ),
            WorkflowRoute(
                id = "review.provider-projection",
                producerModule = "review",
                consumerModules = setOf("provider", "discovery"),
                executionKind = WorkflowExecutionKind.IN_PROCESS_EVENT,
                topic = "reviews.events",
                eventTypes = setOf("ReviewSubmitted"),
                inProcessReplacementReady = true
            ),
            WorkflowRoute(
                id = "provider.discovery-projection",
                producerModule = "provider",
                consumerModules = setOf("discovery"),
                executionKind = WorkflowExecutionKind.IN_PROCESS_EVENT,
                topic = "providers.events",
                eventTypes = setOf(
                    "ProviderApproved",
                    "ProviderUpdated",
                    "ProviderSuspended",
                    "ProviderReactivated",
                    "ProviderRejected"
                ),
                inProcessReplacementReady = true
            ),
            WorkflowRoute(
                id = "provider.vaccination-reminders",
                producerModule = "provider",
                consumerModules = setOf("notification"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "vaccination.events",
                eventTypes = setOf("VaccinationReminderCreated", "VaccinationReminderUpdated"),
                inProcessReplacementReady = true
            ),
            WorkflowRoute(
                id = "chat.message-notification",
                producerModule = "chat",
                consumerModules = setOf("notification"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "chat.events",
                eventTypes = setOf("ChatMessageSent"),
                inProcessReplacementReady = false
            ),
            WorkflowRoute(
                id = "dispatch.lifecycle-fanout",
                producerModule = "dispatch",
                consumerModules = setOf("notification", "captain"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "dispatch.events",
                eventTypes = setOf("DispatchJobOffered", "DispatchJobAccepted", "DispatchJobDelivered", "DispatchJobFailed"),
                inProcessReplacementReady = false
            ),
            WorkflowRoute(
                id = "payment.lifecycle-fanout",
                producerModule = "payment",
                consumerModules = setOf("order", "notification"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "payments.events",
                inProcessReplacementReady = false
            ),
            WorkflowRoute(
                id = "catalog.projection-fanout",
                producerModule = "catalog",
                consumerModules = setOf("discovery"),
                executionKind = WorkflowExecutionKind.IN_PROCESS_EVENT,
                topic = "catalog.events",
                inProcessReplacementReady = false
            ),
            WorkflowRoute(
                id = "support.notification-fanout",
                producerModule = "support",
                consumerModules = setOf("notification"),
                executionKind = WorkflowExecutionKind.DURABLE_OUTBOX_JOB,
                topic = "support.events",
                inProcessReplacementReady = false
            )
        )
    )
}

data class ModuleDomainEvent(
    val eventId: UUID,
    val topic: String,
    val key: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant,
    val shadow: Boolean = false
)

fun interface ModuleEventPublisher {
    fun publish(event: ModuleDomainEvent)
}

class SpringModuleEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : ModuleEventPublisher {
    override fun publish(event: ModuleDomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
