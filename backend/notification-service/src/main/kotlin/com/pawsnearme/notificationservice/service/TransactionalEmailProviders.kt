package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

data class EmailProviderCommand(
    val idempotencyKey: String,
    val recipientEmail: String,
    val recipientName: String?,
    val templateCode: String,
    val variables: Map<String, Any?>,
)

data class EmailProviderResult(
    val provider: String,
    val accepted: Boolean,
    val providerMessageId: String? = null,
    val safeToFailover: Boolean = false,
    val retryable: Boolean = false,
    val ambiguous: Boolean = false,
    val error: String? = null,
)

interface TransactionalEmailProvider {
    val providerName: String
    fun isConfigured(templateCode: String): Boolean
    fun send(command: EmailProviderCommand): EmailProviderResult
}

@Component
class Msg91TransactionalEmailProvider(
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
) : TransactionalEmailProvider {
    override val providerName: String = "MSG91"
    private val client = RestClient.builder().baseUrl("https://control.msg91.com").build()
    private val log = LoggerFactory.getLogger(javaClass)

    override fun isConfigured(templateCode: String): Boolean =
        !authKey().isNullOrBlank() &&
            !domain().isNullOrBlank() &&
            !fromEmail().isNullOrBlank() &&
            !templateId(templateCode).isNullOrBlank()

    override fun send(command: EmailProviderCommand): EmailProviderResult {
        if (!isConfigured(command.templateCode)) {
            return EmailProviderResult(providerName, false, error = "MSG91 email provider/template is not configured")
        }

        val payload = mapOf(
            "recipients" to listOf(
                mapOf(
                    "to" to listOf(
                        mapOf(
                            "name" to (command.recipientName ?: "MyPet Customer"),
                            "email" to command.recipientEmail,
                        )
                    ),
                    "variables" to command.variables,
                )
            ),
            "from" to mapOf(
                "name" to (environment.getProperty("notification.email.msg91.from-name") ?: "MyPet"),
                "email" to fromEmail(),
            ),
            "domain" to domain(),
            "template_id" to templateId(command.templateCode),
            "validate_before_send" to true,
        )

        return try {
            val response = client.post()
                .uri("/api/v5/email/send")
                .header("accept", "application/json")
                .header("authkey", authKey()!!)
                .header("content-type", "application/json")
                .body(payload)
                .retrieve()
                .toEntity(String::class.java)

            val messageId = response.body?.let(::extractMessageId)
            EmailProviderResult(providerName, true, providerMessageId = messageId)
        } catch (error: RestClientResponseException) {
            classifyResponseFailure(error.statusCode, error.responseBodyAsString)
        } catch (error: ResourceAccessException) {
            log.warn("MSG91 email request ended without a definitive provider response: {}", error.message)
            EmailProviderResult(
                providerName,
                accepted = false,
                retryable = true,
                ambiguous = true,
                error = "MSG91 request outcome is unknown: ${error.message}",
            )
        } catch (error: Exception) {
            log.error("MSG91 email send failed", error)
            EmailProviderResult(providerName, false, retryable = true, error = error.message)
        }
    }

    private fun classifyResponseFailure(status: HttpStatusCode, body: String): EmailProviderResult {
        val normalized = body.lowercase()
        val quotaFailure = listOf("quota", "credit", "balance", "limit", "exhaust").any(normalized::contains)
        val safeToFailover = status.value() == 402 || status.value() == 429 || status.is5xxServerError || quotaFailure
        return EmailProviderResult(
            provider = providerName,
            accepted = false,
            safeToFailover = safeToFailover,
            retryable = safeToFailover,
            error = "MSG91 HTTP ${status.value()}: ${body.take(600)}",
        )
    }

    private fun extractMessageId(body: String): String? = runCatching {
        val root = objectMapper.readTree(body)
        sequenceOf("messageId", "message_id", "request_id", "requestId")
            .mapNotNull { key -> root.path(key).takeIf { !it.isMissingNode && !it.isNull }?.asText()?.takeIf(String::isNotBlank) }
            .firstOrNull()
    }.getOrNull()

    private fun authKey() = environment.getProperty("notification.email.msg91.auth-key")
    private fun domain() = environment.getProperty("notification.email.msg91.domain")
    private fun fromEmail() = environment.getProperty("notification.email.msg91.from-email")
    private fun templateId(code: String) = environment.getProperty("notification.email.msg91.templates.$code")
}

@Component
class BrevoTransactionalEmailProvider(
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
) : TransactionalEmailProvider {
    override val providerName: String = "BREVO"
    private val client = RestClient.builder().baseUrl("https://api.brevo.com").build()
    private val log = LoggerFactory.getLogger(javaClass)

    override fun isConfigured(templateCode: String): Boolean =
        !apiKey().isNullOrBlank() && templateId(templateCode) != null

    override fun send(command: EmailProviderCommand): EmailProviderResult {
        val templateId = templateId(command.templateCode)
            ?: return EmailProviderResult(providerName, false, error = "Brevo template is not configured for ${command.templateCode}")
        val key = apiKey()
            ?: return EmailProviderResult(providerName, false, error = "Brevo API key is not configured")

        val payload = mutableMapOf<String, Any?>(
            "to" to listOf(
                mapOf(
                    "email" to command.recipientEmail,
                    "name" to (command.recipientName ?: "MyPet Customer"),
                )
            ),
            "templateId" to templateId,
            "params" to command.variables,
            "headers" to mapOf("Idempotency-Key" to command.idempotencyKey),
        )

        val senderEmail = environment.getProperty("notification.email.brevo.from-email")?.takeIf(String::isNotBlank)
        if (senderEmail != null) {
            payload["sender"] = mapOf(
                "email" to senderEmail,
                "name" to (environment.getProperty("notification.email.brevo.from-name") ?: "MyPet"),
            )
        }

        return try {
            val response = client.post()
                .uri("/v3/smtp/email")
                .header("accept", "application/json")
                .header("api-key", key)
                .header("content-type", "application/json")
                .body(payload)
                .retrieve()
                .toEntity(String::class.java)

            val messageId = response.body?.let { body ->
                runCatching { objectMapper.readTree(body).path("messageId").asText(null) }.getOrNull()
            }
            EmailProviderResult(providerName, true, providerMessageId = messageId)
        } catch (error: RestClientResponseException) {
            val retryable = error.statusCode.value() == 429 || error.statusCode.is5xxServerError
            EmailProviderResult(
                provider = providerName,
                accepted = false,
                retryable = retryable,
                error = "Brevo HTTP ${error.statusCode.value()}: ${error.responseBodyAsString.take(600)}",
            )
        } catch (error: ResourceAccessException) {
            log.warn("Brevo email request ended without a definitive provider response: {}", error.message)
            EmailProviderResult(
                providerName,
                accepted = false,
                retryable = true,
                ambiguous = true,
                error = "Brevo request outcome is unknown: ${error.message}",
            )
        } catch (error: Exception) {
            log.error("Brevo email send failed", error)
            EmailProviderResult(providerName, false, retryable = true, error = error.message)
        }
    }

    private fun apiKey() = environment.getProperty("notification.email.brevo.api-key")
    private fun templateId(code: String): Long? =
        environment.getProperty("notification.email.brevo.templates.$code")?.trim()?.toLongOrNull()
}
