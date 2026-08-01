package com.pawsnearme.notificationservice.events

import com.pawsnearme.common.events.ModuleDomainEvent
import com.pawsnearme.notificationservice.service.AppointmentEventListener
import com.pawsnearme.notificationservice.service.ChatEventListener
import com.pawsnearme.notificationservice.service.OrderEventListener
import com.pawsnearme.notificationservice.service.VaccinationEventListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "mypet.events",
    name = ["delivery-mode"],
    havingValue = "IN_PROCESS_ONLY"
)
class InProcessNotificationEventBridge(
    private val orderEventListener: OrderEventListener,
    private val appointmentEventListener: AppointmentEventListener,
    private val chatEventListener: ChatEventListener,
    private val vaccinationEventListener: VaccinationEventListener
) {
    @EventListener
    fun onModuleEvent(event: ModuleDomainEvent) {
        if (event.shadow) return
        when (event.topic) {
            "orders.events" -> orderEventListener.onOrderEvent(event.payload)
            "appointments.events" -> appointmentEventListener.onAppointmentEvent(event.payload)
            "chat.events" -> chatEventListener.onChatEvent(event.payload)
            "vaccination.events" -> vaccinationEventListener.onVaccinationEvent(event.payload)
        }
    }
}
