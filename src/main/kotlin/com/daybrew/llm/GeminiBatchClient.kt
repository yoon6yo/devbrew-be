package com.daybrew.llm

import com.daybrew.config.DayBrewProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

data class BatchRequest(val key: String, val body: Map<String, Any>)

@Component
class GeminiBatchClient(
    private val webClient: WebClient,
    private val props: DayBrewProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    fun submitAndAwait(
        requests: List<BatchRequest>,
        displayName: String = "daybrew-batch",
        timeoutMinutes: Int = 30,
    ): Map<String, Map<String, Any>> {
        if (requests.isEmpty()) return emptyMap()
        val apiKey = props.gemini.apiKey
        val result = mutableMapOf<String, Map<String, Any>>()

        requests.forEach { req ->
            runCatching {
                val resp = webClient.post()
                    .uri("${props.gemini.baseUrl}/v1/models/${props.gemini.model}:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(req.body)
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block() as Map<String, Any>? ?: throw RuntimeException("Null response for ${req.key}")
                result[req.key] = resp
            }.onFailure { log.warn("generateContent failed for ${req.key}", it) }
        }

        log.info("Batch complete — ${result.size}/${requests.size} succeeded ($displayName)")
        return result
    }
}
