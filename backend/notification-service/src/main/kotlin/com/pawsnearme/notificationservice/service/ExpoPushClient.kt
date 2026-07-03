package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

data class ExpoPushSendResult(
    val success: Boolean,
    val sentCount: Int,
    val failureReason: String? = null,
)

@Component
class ExpoPushClient(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    fun send(
        tokens: List<String>,
        title: String,
        body: String,
        sound: String? = "default",
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
    ): ExpoPushSendResult {
        if (tokens.isEmpty()) {
            return ExpoPushSendResult(success = false, sentCount = 0, failureReason = "No push tokens")
        }

        val messages = tokens.map { token ->
            buildMap {
                put("to", token)
                put("title", title)
                put("body", body)
                put("priority", "high")
                if (sound != null) put("sound", sound)
                if (channelId != null) put("channelId", channelId)
                if (data.isNotEmpty()) put("data", data)
            }
        }

        return try {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val payload = objectMapper.writeValueAsString(messages)
            val response = restTemplate.postForEntity(
                "https://exp.host/--/api/v2/push/send",
                HttpEntity(payload, headers),
                Map::class.java,
            )
            val bodyMap = response.body
            val dataList = bodyMap?.get("data") as? List<*>
            val errors = dataList?.mapNotNull { row ->
                (row as? Map<*, *>)?.get("message")?.toString()
            }?.filter { it.isNotBlank() } ?: emptyList()

            if (errors.isNotEmpty()) {
                log.warn("Expo push partial failure: {}", errors.joinToString("; "))
            }

            ExpoPushSendResult(
                success = response.statusCode.is2xxSuccessful && errors.size < tokens.size,
                sentCount = tokens.size - errors.size,
                failureReason = errors.firstOrNull(),
            )
        } catch (e: Exception) {
            log.error("Expo push request failed: {}", e.message, e)
            ExpoPushSendResult(success = false, sentCount = 0, failureReason = e.message)
        }
    }
}
