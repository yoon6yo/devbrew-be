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
    private val adminStatsService: com.devbrew.admin.AdminStatsService,
) : IdeaRater {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun rateAll(ideas: List<Idea>): Map<Long, ScoreResult> {
        if (ideas.isEmpty()) return emptyMap()

        log.info("Rating ${ideas.size} ideas via Gemini (concurrent)")

        return Flux.fromIterable(ideas)
            .flatMap { idea ->
                rateOne(idea).onErrorResume { e ->
                    log.warn("Rating failed for idea ${idea.id}", e)
                    Mono.empty<Pair<Long, ScoreResult>>()
                }
            }
            .collectList()
            .block()
            ?.toMap()
            ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun rateOne(idea: Idea): Mono<Pair<Long, ScoreResult>> {
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

                fun score(key: String) = (parsed[key] as Number).toInt().coerceIn(1, 10).toShort()
                val marketFit    = score("market_fit")
                val novelty      = score("novelty")
                val feasibility  = score("feasibility")
                val monetization = score("monetization")
                val trend        = score("trend")

                // Weighted: market_fit 30%, novelty 20%, feasibility 20%, monetization 20%, trend 10%
                val weighted = (marketFit * 0.30 + novelty * 0.20 + feasibility * 0.20 +
                        monetization * 0.20 + trend * 0.10).toInt().coerceIn(1, 10).toShort()

                val reason = parsed["reason"] as String

                (response["usageMetadata"] as? Map<*, *>)?.let { usage ->
                    val prompt = (usage["promptTokenCount"] as? Number)?.toInt() ?: 0
                    val completion = (usage["candidatesTokenCount"] as? Number)?.toInt() ?: 0
                    runCatching { adminStatsService.recordGeminiUsage("rate", prompt, completion) }
                }

                idea.id!! to ScoreResult(weighted, marketFit, novelty, feasibility, monetization, trend, reason)
            }
    }

    private fun ratingPrompt(idea: Idea): String =
        """You are a startup idea evaluator. Rate the following idea on five axes, each 1–10.

Title: ${idea.title}
Description: ${idea.description}
Source: ${idea.sourceTrack}

Axes:
- market_fit: Is there a real, painful problem? Is there a clearly defined audience willing to pay?
- novelty: How differentiated is this from existing solutions?
- feasibility: Can a small team (1-3 devs) realistically build and ship an MVP within 3 months?
- monetization: Is there a clear, near-term revenue path (SaaS, marketplace fee, API pricing, etc.)?
- trend: Does this ride a current wave (AI, no-code, developer tooling, etc.)?

Respond with JSON only:
{"market_fit":<1-10>,"novelty":<1-10>,"feasibility":<1-10>,"monetization":<1-10>,"trend":<1-10>,"reason":"<under 120 words summarising strengths and key risk>"}"""
}
