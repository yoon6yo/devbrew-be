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
        val apiKey = props.gemini.apiKey
        val submitBody = mapOf(
            "batch" to mapOf(
                "display_name" to displayName,
                "input_config" to mapOf(
                    "requests" to mapOf(
                        "requests" to requests.map { r ->
                            mapOf("request" to r.body, "metadata" to mapOf("key" to r.key))
                        }
                    )
                )
            )
        )

        val submitResp = webClient.post()
            .uri("${props.gemini.baseUrl}/v1beta/models/${props.gemini.model}:batchGenerateContent")
            .header("x-goog-api-key", apiKey)
            .bodyValue(submitBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() as Map<String, Any>? ?: throw RuntimeException("Batch submit returned null")

        val batchName = submitResp["name"] as? String ?: throw RuntimeException("No batch name in response: $submitResp")
        log.info("Batch submitted: $batchName (${requests.size} requests)")

        return poll(batchName, apiKey, timeoutMinutes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun poll(batchName: String, apiKey: String, timeoutMinutes: Int): Map<String, Map<String, Any>> {
        val deadline = System.currentTimeMillis() + timeoutMinutes * 60_000L
        var intervalMs = 5_000L

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(intervalMs)
            intervalMs = minOf(intervalMs * 2, 60_000L)

            val status = webClient.get()
                .uri("${props.gemini.baseUrl}/v1beta/$batchName")
                .header("x-goog-api-key", apiKey)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as Map<String, Any>? ?: continue

            val state = status["state"] as? String ?: continue
            log.info("Batch $batchName state: $state")

            when (state) {
                "JOB_STATE_SUCCEEDED" -> {
                    val responses = status["responses"] as? List<Map<String, Any>> ?: emptyList()
                    return responses.associate { item ->
                        val key = (item["metadata"] as? Map<*, *>)?.get("key") as? String ?: ""
                        val response = item["response"] as? Map<String, Any> ?: emptyMap()
                        key to response
                    }
                }
                "JOB_STATE_FAILED"    -> throw RuntimeException("Batch job failed: ${status["error"]}")
                "JOB_STATE_CANCELLED" -> throw RuntimeException("Batch job was cancelled")
                "JOB_STATE_EXPIRED"   -> throw RuntimeException("Batch job expired before completion")
            }
        }
        throw RuntimeException("Batch job timed out after $timeoutMinutes minutes")
    }
}
