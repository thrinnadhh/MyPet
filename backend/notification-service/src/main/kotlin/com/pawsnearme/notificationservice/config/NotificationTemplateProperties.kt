package com.pawsnearme.notificationservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.stereotype.Component

/**
 * Template registry loaded from application.yml under `notification.templates`.
 * Each entry maps a template code (e.g. "APPOINTMENT_T24H") to a message pattern.
 * Use {referenceId} as a placeholder — it is replaced at dispatch time.
 *
 * Example YAML:
 *   notification:
 *     templates:
 *       APPOINTMENT_T24H: "Your appointment is tomorrow! Ref: {referenceId}"
 *       APPOINTMENT_T1H:  "Your appointment is in 1 hour! Ref: {referenceId}"
 */
@Component
@ConfigurationProperties(prefix = "notification")
class NotificationTemplateProperties {

    /** Map of templateCode → message pattern. */
    var templates: Map<String, String> = emptyMap()

    /** Returns the rendered message for the given template code, or a generic fallback. */
    fun messageFor(templateCode: String, referenceId: String): String {
        val pattern = templates[templateCode]
            ?: return "Reminder for: $referenceId"
        return pattern.replace("{referenceId}", referenceId)
    }
}
