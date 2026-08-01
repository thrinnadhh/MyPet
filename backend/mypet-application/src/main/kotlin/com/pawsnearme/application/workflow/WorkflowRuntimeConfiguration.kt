package com.pawsnearme.application.workflow

import com.pawsnearme.common.events.MyPetWorkflowCatalog
import com.pawsnearme.common.events.WorkflowCatalog
import com.pawsnearme.common.events.WorkflowExecutionKind
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration(proxyBeanMethods = false)
class WorkflowRuntimeConfiguration {
    @Bean
    fun workflowCatalog(): WorkflowCatalog = MyPetWorkflowCatalog.create()
}

@Component
class WorkflowRuntimeInfoContributor(
    private val workflowCatalog: WorkflowCatalog,
    @Value("\${mypet.events.delivery-mode:KAFKA_ONLY}") private val deliveryMode: String
) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        val eventRoutes = workflowCatalog.routes.filter { it.topic != null }
        builder.withDetail(
            "workflowRuntime",
            mapOf(
                "milestone" to "M6",
                "deliveryMode" to deliveryMode.trim().uppercase(),
                "routeCount" to workflowCatalog.routes.size,
                "directCallCount" to workflowCatalog.count(WorkflowExecutionKind.DIRECT_CALL),
                "inProcessEventCount" to workflowCatalog.count(WorkflowExecutionKind.IN_PROCESS_EVENT),
                "durableOutboxJobCount" to workflowCatalog.count(WorkflowExecutionKind.DURABLE_OUTBOX_JOB),
                "verifiedInProcessTopics" to eventRoutes
                    .filter { it.inProcessReplacementReady }
                    .mapNotNull { it.topic }
                    .distinct()
                    .sorted(),
                "pendingReplacementTopics" to eventRoutes
                    .filterNot { it.inProcessReplacementReady }
                    .mapNotNull { it.topic }
                    .distinct()
                    .sorted(),
                "kafkaRollbackRetained" to workflowCatalog.routes.any { it.kafkaRollbackRetained }
            )
        )
    }
}
