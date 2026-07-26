package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.Idea
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class GeminiIdeaRater(
    private val webClient: WebClient,
    private val props: DevBrewProperties,
    private val objectMapper: ObjectMapper,
) : IdeaRater {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun rateAll(ideas: List<Idea>): Map<Long, Pair<Short, String>> {
        if (ideas.isEmpty()) return emptyMap()

        log.info("Rating ${ideas.size} ideas via Gemini (concurrent)")

        return Flux.fromIterable(ideas)
            .flatMap { idea ->
                rateOne(idea).onErrorResume { e ->
                    log.warn("Rating failed for idea ${idea.id}", e)
                    Mono.empty()
                }
            }
            .collectList()
            .block()
            ?.toMap()
            ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun rateOne(idea: Idea): Mono<Pair<Long, Pair<Short, String>>> {
        val uri = "${props.gemini.baseUrl}/v1beta/models/${props.gemini.model}:generateContent"
        return webClient.post()
            .uri(uri)
            .header("x-goog-api-key", props.gemini.apiKey)
            .bodyValue(mapOf(
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to ratingPrompt(idea))))),
                "generationConfig" to mapOf("responseMimeType" to "application/json"),
            ))
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                val candidates = response["candidates"] as List<Map<*, *>>
                val parts = (candidates[0]["content"] as Map<*, *>)["parts"] as List<Map<*, *>>
                val text = parts[0]["text"] as String
                val parsed = objectMapper.readValue(text, Map::class.java)
                val score = (parsed["score"] as Number).toInt().coerceIn(1, 10).toShort()
                val reason = parsed["reason"] as String
                idea.id!! to (score to reason)
            }
    }

    private fun ratingPrompt(idea: Idea): String =
        """Rate this startup idea on a scale of 1-10.

Title: ${idea.title}
Description: ${idea.description}
Source: ${idea.sourceTrack}

Respond with JSON only: {"score": <1-10>, "reason": "<explanation under 100 words>"}"""
}
