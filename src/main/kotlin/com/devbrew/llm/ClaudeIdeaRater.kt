package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.Idea
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ClaudeIdeaRater(
    private val webClient: WebClient,
    private val props: DevBrewProperties,
    private val objectMapper: ObjectMapper,
) : IdeaRater {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun rateAll(ideas: List<Idea>): Map<Long, Pair<Short, String>> {
        if (ideas.isEmpty()) return emptyMap()

        val batchId = submitBatch(ideas)
        log.info("Submitted Claude batch $batchId for ${ideas.size} ideas")

        waitForCompletion(batchId)

        return fetchResults(batchId)
    }

    private fun submitBatch(ideas: List<Idea>): String {
        val requests = ideas.map { idea ->
            mapOf(
                "custom_id" to idea.id.toString(),
                "params" to mapOf(
                    "model" to "claude-haiku-4-5-20251001",
                    "max_tokens" to 256,
                    "messages" to listOf(mapOf("role" to "user", "content" to ratingPrompt(idea))),
                )
            )
        }

        @Suppress("UNCHECKED_CAST")
        val response = webClient.post()
            .uri("${props.claude.baseUrl}/v1/messages/batches")
            .headers { it.claudeHeaders(props.claude.apiKey) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("requests" to requests))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() as Map<String, Any>? ?: throw RuntimeException("Empty batch submission response")

        return response["id"] as String
    }

    private fun waitForCompletion(batchId: String) {
        for (i in 0 until props.claude.batchMaxPolls) {
            if (pollStatus(batchId) == "ended") return
            log.debug("Batch $batchId still processing (poll ${i + 1}/${props.claude.batchMaxPolls})")
            Thread.sleep(props.claude.batchPollIntervalMs)
        }
        throw RuntimeException("Batch $batchId timed out after ${props.claude.batchMaxPolls} polls")
    }

    @Suppress("UNCHECKED_CAST")
    private fun pollStatus(batchId: String): String {
        val response = webClient.get()
            .uri("${props.claude.baseUrl}/v1/messages/batches/$batchId")
            .headers { it.claudeHeaders(props.claude.apiKey) }
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() as Map<String, Any>? ?: throw RuntimeException("Empty poll response")

        return response["processing_status"] as String
    }

    private fun fetchResults(batchId: String): Map<Long, Pair<Short, String>> {
        val body = webClient.get()
            .uri("${props.claude.baseUrl}/v1/messages/batches/$batchId/results")
            .headers { it.claudeHeaders(props.claude.apiKey) }
            .retrieve()
            .bodyToMono(String::class.java)
            .block() ?: return emptyMap()

        return body.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { parseResultLine(it) }
            .toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResultLine(line: String): Pair<Long, Pair<Short, String>>? {
        return try {
            val obj = objectMapper.readValue(line, Map::class.java)
            val customId = (obj["custom_id"] as String).toLong()
            val result = obj["result"] as Map<*, *>

            if (result["type"] != "succeeded") {
                log.warn("Non-success result for custom_id=$customId: ${result["type"]}")
                return null
            }

            val message = result["message"] as Map<*, *>
            val text = ((message["content"] as List<Map<*, *>>)[0])["text"] as String
            val scoreJson = objectMapper.readValue(text, Map::class.java)

            val score = (scoreJson["score"] as Number).toInt().coerceIn(1, 10).toShort()
            val reason = scoreJson["reason"] as String

            customId to (score to reason)
        } catch (e: Exception) {
            log.warn("Failed to parse batch result line: $line", e)
            null
        }
    }

    private fun ratingPrompt(idea: Idea): String =
        """Rate this startup idea on a scale of 1-10.

Title: ${idea.title}
Description: ${idea.description}
Source: ${idea.sourceTrack}

Respond with JSON only: {"score": <1-10>, "reason": "<explanation under 100 words>"}"""

    private fun org.springframework.http.HttpHeaders.claudeHeaders(apiKey: String) {
        set("x-api-key", apiKey)
        set("anthropic-version", "2023-06-01")
        set("anthropic-beta", "message-batches-2024-09-24")
    }
}
